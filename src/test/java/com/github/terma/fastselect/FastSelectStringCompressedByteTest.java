/*
Copyright 2015-2017 Artem Stasiuk

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

import com.github.terma.fastselect.data.StringCompressedByte;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@SuppressWarnings("WeakerAccess")
public class FastSelectStringCompressedByteTest {

    @Test
    public void shouldSelectAndSortByColumn() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).create();
        database.addAll(asList(
                new TestString("11"),
                new TestString("4"),
                new TestString("12"),
                new TestString("98")));

        Assert.assertEquals(asList(
                new TestString("11"),
                new TestString("12"),
                new TestString("4"),
                new TestString("98")),
                database.selectAndSort(new Request[0], "stringValue"));
    }

    @Test
    public void shouldSelectIfManyBlocksOneLevel() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(asList(
                new TestString("0"),
                new TestString("12"),
                new TestString("0")));

        List result = database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "12"));

        Assert.assertEquals(singletonList(new TestString("12")), result);
    }

    @Test
    public void shouldSupportAddMultipleTimes() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(singletonList(new TestString("Short.MAX_VALUE")));
        database.addAll(singletonList(new TestString("0")));
        database.addAll(singletonList(new TestString("Short.MIN_VALUE")));

        List result = database.select();

        Assert.assertEquals(asList(
                new TestString("Short.MAX_VALUE"),
                new TestString("0"),
                new TestString("Short.MIN_VALUE")
        ), result);
    }

    @Test
    public void shouldSelect() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(asList(
                new TestString("A"),
                new TestString("aRa"),
                new TestString("ArM")));

        Assert.assertEquals(
                Collections.emptyList(),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "RaR")));

        Assert.assertEquals(
                Collections.singletonList(new TestString("ArM")),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "ArM")));

        Assert.assertEquals(
                asList(new TestString("aRa"), new TestString("ArM")),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "ar")));
    }

    @Test
    public void shouldSelectIfOneOfValueIsNull() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(asList(
                new TestString(null),
                new TestString("aRa")));

        Assert.assertEquals(
                Collections.singletonList(new TestString("aRa")),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "ar")));
    }

    @Test
    public void shouldSelectNullByEmpty() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(Collections.singletonList(new TestString(null)));

        Assert.assertEquals(
                Collections.singletonList(new TestString(null)),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "")));
    }

    @Test
    public void shouldSelectAllByEmpty() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(asList(new TestString(null), new TestString(""), new TestString("A")));

        Assert.assertEquals(
                asList(new TestString(null), new TestString(""), new TestString("A")),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "")));
    }

    @Test
    public void shouldSelectByMultipleAsOr() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).blockSize(1).create();
        database.addAll(asList(new TestString("Zorro"), new TestString("Dora"), new TestString("Ara")));

        Assert.assertEquals(
                asList(new TestString("Zorro"), new TestString("Dora")),
                database.select(new StringCompressedByteNoCaseLikeRequest("stringValue", "oR", "rr")));
    }

    @Test
    public void shouldCorrectlyRestoreField() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).create();
        database.addAll(asList(
                new TestString("0"),
                new TestString("-1"),
                new TestString("1")));

        Assert.assertEquals(asList(
                new TestString("0"),
                new TestString("-1"),
                new TestString("1")),
                database.select());
    }

    @Test
    public void shouldCorrectlyCopy() {
        FastSelect<TestString> database = new FastSelectBuilder<>(TestString.class).create();
        database.addAll(asList(
                new TestString("0"),
                new TestString("-1"),
                new TestString("1")));

        Assert.assertEquals(asList(
                new TestString("0"),
                new TestString("-1"),
                new TestString("1")),
                database.copy(new Request[0]).select());
    }

    public static class TestString {

        @StringCompressedByte
        public String stringValue;

        // empty constructor for database to be able restore object
        @SuppressWarnings("unused")
        public TestString() {
            this("");
        }

        TestString(String stringValue) {
            this.stringValue = stringValue;
        }

        @Override
        public String toString() {
            return "TestString{stringValue=" + stringValue + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestString TestString = (TestString) o;

            return Objects.equals(stringValue, TestString.stringValue);

        }

        @Override
        public int hashCode() {
            return stringValue.hashCode();
        }
    }

}
