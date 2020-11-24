package io.mycat.calcite.sqlfunction.stringfunction;


import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.Types;
import org.apache.calcite.mycat.MycatSqlDefinedFunction;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeFamily;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class BitOrFunction extends MycatSqlDefinedFunction {


    public static final BitOrFunction INSTANCE = new BitOrFunction();

    public BitOrFunction() {
        super("|", ReturnTypes.BIGINT, InferTypes.FIRST_KNOWN, OperandTypes.family(
                SqlTypeFamily.NUMERIC,
                SqlTypeFamily.NUMERIC
                ), null, SqlFunctionCategory.NUMERIC);

    }

    @Override
    public Expression implement(RexToLixTranslator translator, RexCall call, RexImpTable.NullAs nullAs) {
        Method makeSet = Types.lookupMethod(BitOrFunction.class,
                "bitOr", Long.class, Long.class);
        return Expressions.call(makeSet,translator.translateList(call.getOperands(),nullAs));
    }

    public static Long bitOr(Long left,Long right) {
        if (left == null || right == null) {
            return null;
        }
        return left|right;
    }
}