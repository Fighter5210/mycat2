/**
 * Copyright (C) <2021>  <mycat>
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <http://www.gnu.org/licenses/>.
 */
package io.mycat.router.mycat1xfunction;

import io.mycat.router.CustomRuleFunction;
import io.mycat.router.Mycat1xSingleValueRuleFunction;
import io.mycat.router.ShardingTableHandler;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

public class PartitionByHashMod extends Mycat1xSingleValueRuleFunction {

    private int count;
    private boolean watch;

    @Override
    public String name() {
        return "PartitionByHashMod";
    }

    @Override
    public int calculateIndex(String columnValue) {
        BigInteger bigNum = BigInteger.valueOf(hash(columnValue.hashCode())).abs();
        if (watch) {
            return bigNum.intValue() & (count - 1);
        }
        return (bigNum.mod(BigInteger.valueOf(count))).intValue();
    }

    @Override
    public int[] calculateIndexRange(String beginValue, String endValue) {
        return null;
    }


    @Override
    public void init(ShardingTableHandler tableHandler, Map<String, Object> prot, Map<String, Object> ranges) {
        this.watch = false;
        this.count = Integer.parseInt(Objects.toString(prot.get("count")));

        if ((count & (count - 1)) == 0) {
            watch = true;
        }
    }

    /**
     * Using Wang/Jenkins Hash
     *
     * @return hash value
     */
    protected int hash(int key) {
        key = (~key) + (key << 21); // key = (key << 21) - key - 1;
        key = key ^ (key >> 24);
        key = (key + (key << 3)) + (key << 8); // key * 265
        key = key ^ (key >> 14);
        key = (key + (key << 2)) + (key << 4); // key * 21
        key = key ^ (key >> 28);
        key = key + (key << 31);
        return key;
    }

    @Override
    public boolean isSameDistribution(CustomRuleFunction customRuleFunction) {
        if (customRuleFunction == null) return false;
        if (PartitionByHashMod.class.isAssignableFrom(customRuleFunction.getClass())) {
            PartitionByHashMod partitionByHashMod = (PartitionByHashMod) customRuleFunction;
            int count = partitionByHashMod.count;
            boolean watch = partitionByHashMod.watch;
            return this.count == count && this.watch == watch;
        }
        return false;
    }
    @Override
    public String getErUniqueID() {
        return  getClass().getName()+":"+ count + watch;
    }
}