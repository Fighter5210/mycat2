/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
/*
 *     Copyright (C) <2021>  <Junwen Chen>
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.ordinate.engine.function.constant;

import io.ordinate.engine.function.SymbolFunction;

public class SymbolConstant extends SymbolFunction implements ConstantFunction {
    public static final SymbolConstant NULL = new SymbolConstant(null);
    public static final SymbolConstant TRUE = new SymbolConstant("true");
    public static final SymbolConstant FALSE = new SymbolConstant("false");
    final CharSequence value;
    public SymbolConstant(CharSequence value){
        this.value = value;
    }
    public static SymbolConstant newInstance(CharSequence value) {
        if (value == null) {
            return NULL;
        }

        if (SymbolConstant.TRUE.value.equals(value)) {
            return TRUE;
        }

        if (SymbolConstant.FALSE.value.equals(value)) {
            return FALSE;
        }

        return new SymbolConstant(value);
    }
}
