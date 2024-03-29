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
package io.mycat.hint;

import lombok.Data;

@Data
public class BaselineParameterizeHint extends HintBuilder {
    String sql;

    public static String create(String sql) {
        BaselineParameterizeHint baselineParameterizeHint = new BaselineParameterizeHint();
        baselineParameterizeHint.setSql(sql);
        return baselineParameterizeHint.build();
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    @Override
    public String getCmd() {
        return "BASELINE PARAMETERIZE ";
    }

    @Override
    public String build() {
        return getCmd() +
                sql;
    }
}