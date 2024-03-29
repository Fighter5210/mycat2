/**
 * Copyright (C) <2021>  <chen junwen>
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
package io.mycat.router;

import io.mycat.Partition;
import io.mycat.ShardingTableType;
import io.mycat.SimpleColumnInfo;
import io.mycat.TableHandler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface ShardingTableHandler extends TableHandler {

    CustomRuleFunction function();

    List<Partition> dataNodes();

    ShardingTableType shardingType();

    default  public List<Partition> getPartitionsByTargetName(String name) {
        return dataNodes().stream().filter(i -> i.getTargetName().equals(name)).collect(Collectors.toList());
    }

    @Override
    List<SimpleColumnInfo> getColumns();


    Optional<Iterable<Object[]>> canIndexTableScan(int[] projects);

    Optional<Iterable<Object[]>> canIndexTableScan(int[] projects, int[] filterIndex, Object[] value);

    Optional<Iterable<Object[]>> canIndexTableScan();

    boolean canIndex();

    public int getIndexBColumnName(String name);
}
