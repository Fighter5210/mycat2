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
package io.mycat.calcite.sqlfunction.lockfunction;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.Types;
import org.apache.calcite.mycat.MycatSqlDefinedFunction;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.runtime.NewMycatDataContext;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;

import java.util.List;

public class MycatReleaseLockFunction extends MycatSqlDefinedFunction {
    public final static MycatReleaseLockFunction INSTANCE = new MycatReleaseLockFunction();

    public MycatReleaseLockFunction() {
        super("RELEASE_LOCK",
                ReturnTypes.BIGINT,
              null, OperandTypes.STRING, null, SqlFunctionCategory.SYSTEM);
    }

    @Override
    public Expression implement(RexToLixTranslator translator, RexCall call, RexImpTable.NullAs nullAs) {
        List<Expression> argValueList = translator.translateList(call.getOperands(), nullAs);
        return Expressions.call(DataContext.ROOT,
                Types.lookupMethod(NewMycatDataContext.class,"releaseLock",String.class),argValueList.get(0));
    }
}
