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

package com.github.terma.fastselect.benchmark;

import com.github.terma.fastselect.data.IntData;

public class Stand {

    public static void main(String[] args) {
        IntData intData = new IntData(1000);
        for (int i = 0; i < 1000; i++) {
            intData.allocate(1);
            intData.set(i, 12);
        }
    }

}
