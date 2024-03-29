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

package io.ordinate.engine.function.aggregate;

import io.ordinate.engine.vector.AggregateVectorExpression;
import io.ordinate.engine.schema.InnerType;
import io.ordinate.engine.record.Record;
import io.questdb.cairo.ArrayColumnTypes;
import io.questdb.cairo.ColumnType;
import io.questdb.cairo.map.MapValue;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;

public class SumLongAggregateFunction implements LongAccumulator {
    private int columnIndex;
    private int stackIndex;

    public SumLongAggregateFunction(int columnIndex) {
        this.columnIndex = columnIndex;
    }

    @Override
    public String name() {
        return "sum(int64)";
    }

    @Override
    public void computeFirst(MapValue reduceContext, Record record) {
        reduceContext.putLong(stackIndex, 0);
        computeNext(reduceContext, record);
    }

    @Override
    public void computeNext(MapValue resultValue, Record record) {
        long value = record.getLong(columnIndex);
        boolean isNull = record.isNull(columnIndex);
        if (!isNull) {
            resultValue.addLong(stackIndex, value);
        }
    }

    @Override
    public void allocContext(ArrayColumnTypes columnTypes) {
        this.stackIndex = columnTypes.getColumnCount();
        columnTypes.add(ColumnType.LONG);
    }

    @Override
    public int getInputColumnIndex() {
        return columnIndex;
    }

    @Override
    public InnerType getOutputType() {
        return null;
    }

    @Override
    public InnerType getInputType() {
        return null;
    }

    @Override
    public InnerType getType() {
        return InnerType.INT64_TYPE;
    }

    @Override
    public AggregateVectorExpression toAggregateVectorExpression() {
        return new AggregateVectorExpression() {
            @Override
            public long computeFinalLongValue(MapValue resultValue) {
                return resultValue.getLong(stackIndex);
            }

            @Override
            public void computeUpdateValue(MapValue resultValue, FieldVector input) {
                long value = 0;
                BigIntVector bigIntVector = (BigIntVector) input;
                int valueCount = bigIntVector.getValueCount();
                int nullCount = bigIntVector.getNullCount();
                if (nullCount == 0) {
                    for (int i = 0; i < valueCount; i++) {
                        value += bigIntVector.get(i);
                    }
                } else {
                    for (int i = 0; i < valueCount; i++) {
                        if (!bigIntVector.isNull(i)) {
                            value += bigIntVector.get(i);
                        }
                    }
                }
                resultValue.addLong(stackIndex, value);
            }

            @Override
            public InnerType getType() {
                return SumLongAggregateFunction.this.getType();
            }

            @Override
            public int getInputColumnIndex() {
                return columnIndex;
            }

        };
    }

    @Override
    public void setInputColumnIndex(int index) {
        this.columnIndex = index;
    }

    @Override
    public long getLong(Record rec) {
        return rec.getLong(stackIndex);
    }
}
