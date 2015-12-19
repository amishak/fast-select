/*
Copyright 2015 Artem Stasiuk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.github.terma.fastselect;

import com.github.terma.fastselect.callbacks.ArrayLayoutCallback;
import com.github.terma.fastselect.callbacks.ArrayToObjectCallback;
import com.github.terma.fastselect.callbacks.Callback;
import com.github.terma.fastselect.callbacks.ListCallback;
import com.github.terma.fastselect.data.*;
import com.github.terma.fastselect.utils.MethodHandlerRepository;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;

/**
 * Compact in-memory storage with fast search.
 * <p>
 * In Memory implementation for database table. Supports fast search by any combination of columns.
 * Internal storage has constant size for mem overhead.
 * <p>
 * Storage: Column oriented based on array
 * For example for normal Java object you have 12-16 bytes on object header.
 * Plus fields alignment depends on JVM and hardware architectures. Could be 4-8 bytes alignment.
 * Which means you for one object with one int field. You need to allocate 16 bytes on header and 8 bytes
 * on field on x64 machine with SunJVM. <a href="http://shipilev.net/blog/2014/heapdump-is-a-lie/">Details about JVM object mem layout</a>
 * When you add new object class internally extract required fields from that object and add this to
 * particular column data.
 * <p>
 * As result you don't need to spend mem on millions of object headers and alignment.
 * The downside of that each time when you need to get data back as object some time need to be spend on
 * recreation of your object. Mem as well. You can use references field in data at least for current implementation.
 * <p>
 * Search:
 * Fast Search based two step search algorithm (<a href="https://en.wikipedia.org/wiki/Bloom_filter">Bloom Filter</a>
 * + direct scan within block)
 *
 * @author Artem Stasiuk
 * @see ArrayLayoutCallback
 * @see com.github.terma.fastselect.callbacks.GroupCountCallback
 * @see com.github.terma.fastselect.callbacks.MultiGroupCountCallback
 */
@ThreadSafe
public final class FastSelect<T> {

    private final static int DEFAULT_BLOCK_SIZE = 1000;

    private final int blockSize;
    private final Class<T> dataClass;
    private final MethodHandlerRepository mhRepo;
    private final List<Block> blocks;
    private final List<Column> columns;
    private final Map<String, Column> columnsByNames;

    public FastSelect(final int blockSize, final Class<T> dataClass, final Column... columns) {
        this(blockSize, dataClass, Arrays.asList(columns));
    }

    /**
     * @param blockSize
     * @param dataClass
     * @param columns   list of {@link FastSelect.Column} data for them will be extracted from dataClass object
     *                  and used for filtering.
     */
    public FastSelect(final int blockSize, final Class<T> dataClass, final List<Column> columns) {
        this.blockSize = blockSize;
        this.dataClass = dataClass;
        this.columns = columns;
        this.columnsByNames = initColumnsByName(columns);
        this.mhRepo = new MethodHandlerRepository(dataClass, getColumnsAsMap(columns));
        this.blocks = new ArrayList<>();
    }

    public FastSelect(final Class<T> dataClass, final List<Column> columns) {
        this(DEFAULT_BLOCK_SIZE, dataClass, columns);
    }

    private static Map<String, Column> initColumnsByName(List<Column> columns) {
        Map<String, Column> r = new HashMap<>();
        for (Column column : columns) r.put(column.name, column);
        return r;
    }

    private static Map<String, Class> getColumnsAsMap(List<Column> columns) {
        Map<String, Class> r = new HashMap<>();
        for (Column column : columns) r.put(column.name, column.type);
        return r;
    }

    public void addAll(final List<T> data) {
        for (final T row : data) {
            if (blocks.isEmpty() || blocks.get(blocks.size() - 1).size >= blockSize)
                blocks.add(createBlock(blocks));

            Block block = blocks.get(blocks.size() - 1);
            block.size++;
            for (Column column : columns) {
                try {
                    int indexValue;

                    if (column.type == long.class) {
                        long v = (long) mhRepo.get(column.name).invoke(row);
                        ((LongData) column.data).add(v);
                        block.setColumnBitSet(column, (int) v);

                    } else if (column.type == long[].class) {
                        long[] v = (long[]) mhRepo.get(column.name).invoke(row);
                        ((MultiLongData) column.data).add(v);
                        // set all bits
                        for (long v1 : v) block.setColumnBitSet(column, (int) v1);

                    } else if (column.type == short[].class) {
                        short[] v = (short[]) mhRepo.get(column.name).invoke(row);
                        ((MultiShortData) column.data).add(v);
                        // set all bits
                        for (short v1 : v) block.setColumnBitSet(column, v1);

                    } else if (column.type == byte[].class) {
                        byte[] v = (byte[]) mhRepo.get(column.name).invoke(row);
                        ((MultiByteData) column.data).add(v);
                        // set all bits
                        for (byte v1 : v) block.setColumnBitSet(column, v1);

                    } else if (column.type == int.class) {
                        int v = (int) mhRepo.get(column.name).invoke(row);
                        ((FastIntData) column.data).add(v);
                        block.setColumnBitSet(column, v);

                    } else if (column.type == short.class) {
                        short v = (short) mhRepo.get(column.name).invoke(row);
                        ((ShortData) column.data).add(v);
                        block.setColumnBitSet(column, v);

                    } else if (column.type == byte.class) {
                        byte v = (byte) mhRepo.get(column.name).invoke(row);
                        ((FastByteData) column.data).add(v);
                        block.setColumnBitSet(column, v);

                    } else {
                        throw new IllegalArgumentException("!");
                    }
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }
    }

    private Block createBlock(List<Block> blocks) {
        final Block block = new Block();
        block.start = blocks.isEmpty() ? 0 : blocks.get(blocks.size() - 1).start + blocks.get(blocks.size() - 1).size;
        block.size = 0;
        for (Column column : columns) {
            block.columnBitSets.put(column.name, new BitSet());
        }
        return block;
    }

    public List<T> select(final MultiRequest[] where) {
        ListCallback<T> result = new ListCallback<>();
        select(where, result);
        return result.getResult();
    }

    /**
     * Main search method. Find data good for your where (filter) condition and call {@link ArrayLayoutCallback#data(int)}
     * for each of item in result.
     *
     * @param where    filter criteria. Could be any combination of fields which you provide as columns during
     *                 {@link FastSelect} creation.
     * @param callback callback. Will be called for each item accepted by where.
     */
    public void select(final MultiRequest[] where, final ArrayLayoutCallback callback) {
        for (final MultiRequest condition : where) {
            condition.column = columnsByNames.get(condition.name);
            Arrays.sort(condition.values);
        }
        try {
            for (final Block block : blocks) {
                if (!inBlock(where, block)) continue;

                // block good for direct search
                final int end = block.start + block.size;
                opa:
                for (int i = block.start; i < end; i++) {
                    for (final MultiRequest request : where) {
                        if (!request.column.data.check(i, request.values)) continue opa;
                    }

                    callback.data(i);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void select(final MultiRequest[] where, final Callback<T> callback) {
        select(where, new ArrayToObjectCallback<>(dataClass, columns, mhRepo, callback));
    }

    private boolean inBlock(MultiRequest[] requests, Block block) {
        for (MultiRequest request : requests) {
            final BitSet columnBitSet = block.columnBitSets.get(request.name);

            boolean p = false;
            for (final int value : request.values) {
                p = p | columnBitSet.get(value);
            }
            if (!p) return false;
        }
        return true;
    }

    public int size() {
        return columns.iterator().next().data.size();
    }

    public Map<String, Column> getColumnsByNames() {
        return columnsByNames;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {blockSize: " + blockSize + ", data: " + size()
                + ", indexes: " + columns + ", class: " + dataClass + "}";
    }

    public static class Column {

        public final String name;
        public final Class type;
        public final Data data;

        public Column(final String name, final Class type) {
            this.name = name;
            this.type = type;

            if (type == long.class) {
                data = new LongData();
            } else if (type == long[].class) {
                data = new MultiLongData();
            } else if (type == short[].class) {
                data = new MultiShortData();
            } else if (type == byte[].class) {
                data = new MultiByteData();
            } else if (type == int.class) {
                data = new FastIntData();
            } else if (type == short.class) {
                data = new ShortData();
            } else if (type == byte.class) {
                data = new FastByteData();
            } else {
                throw new IllegalArgumentException("Unsupportable column type: " + type
                        + ". Support byte,short,int,long!");
            }
        }

        @Override
        public String toString() {
            return "Column {name: " + name + ", type: " + type + '}';
        }

        public int valueAsInt(final int position) {
            if (type == byte.class) {
                return ((FastByteData) data).data[position];
            } else if (type == short.class) {
                return ((ShortData) data).data[position];
            } else if (type == int.class) {
                return ((FastIntData) data).data[position];
            } else if (type == long.class) {
                return (int) ((LongData) data).data[position];
            } else {
                throw new IllegalArgumentException("Unknown column type: " + type);
            }
        }

    }

    private static class Block {
        public final Map<String, BitSet> columnBitSets = new HashMap<>();
        public int start;
        public int size;

        public void setColumnBitSet(Column column, int bit) {
            columnBitSets.get(column.name).set(bit);
        }

    }

}
