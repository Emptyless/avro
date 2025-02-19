/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro.util;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Generates schema data as Java objects with random values. */
public class RandomData implements Iterable<Object> {
  public static final String USE_DEFAULT = "use-default";
  private final GenericData genericData;

  private static final int MILLIS_IN_DAY = (int) Duration.ofDays(1).toMillis();

  private final Schema root;
  private final long seed;
  private final int count;
  private final boolean utf8ForString;

  public RandomData(Schema schema, int count) {
    this(schema, count, false);
  }

  public RandomData(Schema schema, int count, long seed) {
    this(schema, count, seed, false);
  }

  public RandomData(Schema schema, int count, boolean utf8ForString) {
    this(schema, count, System.currentTimeMillis(), utf8ForString);
  }

  public RandomData(Schema schema, int count, long seed, boolean utf8ForString) {
    this(GenericData.get(), schema, count, seed, utf8ForString);
  }

  public RandomData(GenericData genericData, Schema schema, int count) {
    this(genericData, schema, count, false);
  }

  public RandomData(GenericData genericData, Schema schema, int count, long seed) {
    this(genericData, schema, count, seed, false);
  }

  public RandomData(GenericData genericData, Schema schema, int count, boolean utf8ForString) {
    this(genericData, schema, count, System.currentTimeMillis(), utf8ForString);
  }

  public RandomData(GenericData genericData, Schema schema, int count, long seed, boolean utf8ForString) {
    this.genericData = genericData;
    this.root = schema;
    this.seed = seed;
    this.count = count;
    this.utf8ForString = utf8ForString;
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<Object>() {
      private int n;
      private final Random random = new Random(seed);

      @Override
      public boolean hasNext() {
        return n < count;
      }

      @Override
      public Object next() {
        n++;
        return generate(root, null, random, 0);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private Object generate(Schema schema, Schema.Field field, Random random, int d) {
    switch (schema.getType()) {
    case RECORD:
      return generateRecord(schema, field, random, d);
    case ENUM:
      return generateEnum(schema, field, random, d);
    case ARRAY:
      return generateArray(schema, field, random, d);
    case MAP:
      return generateMap(schema, field, random, d);
    case UNION:
      return generateUnion(schema, field, random, d);
    case FIXED:
      return generateFixed(schema, field, random, d);
    case STRING:
      return generateString(schema, field, random, d);
    case BYTES:
      return generateBytes(schema, field, random, d);
    case INT:
      return generateInt(schema, field, random, d);
    case LONG:
      return generateLong(schema, field, random, d);
    case FLOAT:
      return generateFloat(schema, field, random, d);
    case DOUBLE:
      return generateDouble(schema, field, random, d);
    case BOOLEAN:
      return generateBoolean(schema, field, random, d);
    case NULL:
      return generateNull(schema, field, random, d);
    default:
      throw new RuntimeException("Unknown type: " + schema);
    }
  }

  protected Object generateRecord(Schema schema, Schema.Field field, Random random, int d) {
    Object record = genericData.newRecord(null, schema);
    for (Schema.Field f : schema.getFields()) {
      Object value = (f.getObjectProp(USE_DEFAULT) == null) ? generate(f.schema(), f, random, d + 1)
          : GenericData.get().getDefaultValue(f);
      genericData.setField(record, f.name(), f.pos(), value);
    }
    return record;
  }

  protected Object generateEnum(Schema schema, Schema.Field field, Random random, int d) {
    List<String> symbols = schema.getEnumSymbols();
    return genericData.createEnum(symbols.get(random.nextInt(symbols.size())), schema);
  }

  @SuppressWarnings("unchecked")
  protected Object generateArray(Schema schema, Schema.Field field, Random random, int d) {
    int length = Math.max(0, (random.nextInt(5) + 2) - d);
    GenericArray<Object> array = (GenericArray<Object>) genericData.newArray(null, length, schema);
    for (int i = 0; i < length; i++)
      array.add(generate(schema.getElementType(), null, random, d + 1));
    return array;
  }

  @SuppressWarnings("unchecked")
  protected Object generateMap(Schema schema, Schema.Field field, Random random, int d) {
    int length = Math.max(0, (random.nextInt(5) + 2) - d);
    Map<Object, Object> map = (Map<Object, Object>) genericData.newMap(null, length);
    for (int i = 0; i < length; i++) {
      map.put(randomString(random, 40), generate(schema.getValueType(), null, random, d + 1));
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  protected Object generateUnion(Schema schema, Schema.Field field, Random random, int d) {
    List<Schema> types = schema.getTypes();
    Map<Object, Object> map = (Map<Object, Object>) genericData.newMap(null, 1);
    Schema type = types.get(random.nextInt(types.size()));
    map.put(type.getName(), generate(type, null, random, d));
    return map;
  }

  protected Object generateFixed(Schema schema, Schema.Field field, Random random, int d) {
    byte[] bytes = new byte[schema.getFixedSize()];
    random.nextBytes(bytes);
    return genericData.createFixed(null, bytes, schema);
  }

  protected Object generateString(Schema schema, Schema.Field schemaField, Random random, int d) {
    return randomString(random, 40);
  }

  protected Object generateBytes(Schema schema, Schema.Field schemaField, Random random, int d) {
    return randomBytes(random, 40);
  }

  protected Object generateInt(Schema schema, Schema.Field schemaField, Random random, int d) {
    return this.randomInt(random, schema.getLogicalType());
  }

  protected Object generateLong(Schema schema, Schema.Field schemaField, Random random, int d) {
    return this.randomLong(random, schema.getLogicalType());
  }

  protected Object generateFloat(Schema schema, Schema.Field schemaField, Random random, int d) {
    return random.nextFloat();
  }

  protected Object generateDouble(Schema schema, Schema.Field schemaField, Random random, int d) {
    return random.nextDouble();
  }

  protected Object generateBoolean(Schema schema, Schema.Field schemaField, Random random, int d) {
    return random.nextBoolean();
  }

  protected Object generateNull(Schema schema, Schema.Field schemaField, Random random, int d) {
    return null;
  }

  private static final Charset UTF8 = StandardCharsets.UTF_8;

  protected int randomInt(Random random, LogicalType type) {
    if (type instanceof LogicalTypes.TimeMillis) {
      return random.nextInt(RandomData.MILLIS_IN_DAY - 1);
    }
    // LogicalTypes.Date LocalDate.MAX.toEpochDay() > Integer.MAX;
    return random.nextInt();
  }

  protected long randomLong(Random random, LogicalType type) {
    if (type instanceof LogicalTypes.TimeMicros) {
      return ThreadLocalRandom.current().nextLong(RandomData.MILLIS_IN_DAY * 1000L);
    }
    // For LogicalTypes.TimestampMillis, every long would be OK,
    // Instant.MAX.toEpochMilli() failed and would be > Long.MAX_VALUE.
    return random.nextLong();
  }

  protected Object randomString(Random random, int maxLength) {
    int length = random.nextInt(maxLength);
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) ('a' + random.nextInt('z' - 'a'));
    }
    return utf8ForString ? new Utf8(bytes) : new String(bytes, UTF8);
  }

  protected static ByteBuffer randomBytes(Random rand, int maxLength) {
    ByteBuffer bytes = ByteBuffer.allocate(rand.nextInt(maxLength));
    bytes.limit(bytes.capacity());
    rand.nextBytes(bytes.array());
    return bytes;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3 || args.length > 4) {
      System.out.println("Usage: RandomData <schemafile> <outputfile> <count> [codec]");
      System.exit(-1);
    }
    Schema sch = new Schema.Parser().parse(new File(args[0]));
    try (DataFileWriter<Object> writer = new DataFileWriter<>(new GenericDatumWriter<>())) {
      writer.setCodec(CodecFactory.fromString(args.length >= 4 ? args[3] : "null"));
      writer.setMeta("user_metadata", "someByteArray".getBytes(StandardCharsets.UTF_8));
      File file = new File(args[1]);
      Files.createDirectories(Paths.get(file.getParent()));
      writer.create(sch, file);

      for (Object datum : new RandomData(sch, Integer.parseInt(args[2]))) {
        writer.append(datum);
      }
    }
  }
}
