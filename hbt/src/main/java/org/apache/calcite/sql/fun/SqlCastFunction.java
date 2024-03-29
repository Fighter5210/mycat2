/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql.fun;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.*;
import org.apache.calcite.sql.validate.SqlMonotonicity;
import org.apache.calcite.sql.validate.SqlValidatorImpl;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import org.apache.calcite.util.Util;
import org.jetbrains.annotations.NotNull;

import java.text.Collator;
import java.util.Objects;

import static org.apache.calcite.util.Static.RESOURCE;

/**
 * SqlCastFunction. Note that the std functions are really singleton objects,
 * because they always get fetched via the StdOperatorTable. So you can't store
 * any local info in the class and hence the return type data is maintained in
 * operand[1] through the validation phase.
 *
 * <p>Can be used for both {@link SqlCall} and
 * {@link org.apache.calcite.rex.RexCall}.
 * Note that the {@code SqlCall} has two operands (expression and type),
 * while the {@code RexCall} has one operand (expression) and the type is
 * obtained from {@link org.apache.calcite.rex.RexNode#getType()}.
 *
 * @see SqlCastOperator
 */
public class SqlCastFunction extends SqlFunction {
    //~ Instance fields --------------------------------------------------------

    /**
     * Map of all casts that do not preserve monotonicity.
     */
    private final SetMultimap<SqlTypeFamily, SqlTypeFamily> nonMonotonicCasts =
            ImmutableSetMultimap.<SqlTypeFamily, SqlTypeFamily>builder()
                    .put(SqlTypeFamily.EXACT_NUMERIC, SqlTypeFamily.CHARACTER)
                    .put(SqlTypeFamily.NUMERIC, SqlTypeFamily.CHARACTER)
                    .put(SqlTypeFamily.APPROXIMATE_NUMERIC, SqlTypeFamily.CHARACTER)
                    .put(SqlTypeFamily.DATETIME_INTERVAL, SqlTypeFamily.CHARACTER)
                    .put(SqlTypeFamily.CHARACTER, SqlTypeFamily.EXACT_NUMERIC)
                    .put(SqlTypeFamily.CHARACTER, SqlTypeFamily.NUMERIC)
                    .put(SqlTypeFamily.CHARACTER, SqlTypeFamily.APPROXIMATE_NUMERIC)
                    .put(SqlTypeFamily.CHARACTER, SqlTypeFamily.DATETIME_INTERVAL)
                    .put(SqlTypeFamily.DATETIME, SqlTypeFamily.TIME)
                    .put(SqlTypeFamily.TIMESTAMP, SqlTypeFamily.TIME)
                    .put(SqlTypeFamily.TIME, SqlTypeFamily.DATETIME)
                    .put(SqlTypeFamily.TIME, SqlTypeFamily.TIMESTAMP)
                    .build();

    //~ Constructors -----------------------------------------------------------

    public SqlCastFunction() {
        super("CAST",
                SqlKind.CAST,
                null,
                InferTypes.FIRST_KNOWN,
                null,
                SqlFunctionCategory.SYSTEM);
    }

    //~ Methods ----------------------------------------------------------------

    public RelDataType inferReturnType(
            SqlOperatorBinding opBinding) {
        assert opBinding.getOperandCount() == 2;
        RelDataType ret = opBinding.getOperandType(1);
        RelDataType firstType = opBinding.getOperandType(0);
        ret =
                opBinding.getTypeFactory().createTypeWithNullability(
                        ret,
                        firstType.isNullable());
        if (opBinding instanceof SqlCallBinding) {
            SqlCallBinding callBinding = (SqlCallBinding) opBinding;
            SqlNode operand0 = callBinding.operand(0);

            // dynamic parameters and null constants need their types assigned
            // to them using the type they are casted to.
            if (((operand0 instanceof SqlLiteral)
                    && (((SqlLiteral) operand0).getValue() == null))
                    || (operand0 instanceof SqlDynamicParam)) {
                final SqlValidatorImpl validator =
                        (SqlValidatorImpl) callBinding.getValidator();
                validator.setValidatedNodeType(operand0, ret);
            }
        }
        return ret;
    }

    public String getSignatureTemplate(final int operandsCount) {
        assert operandsCount == 2;
        return "{0}({1} AS {2})";
    }

    public SqlOperandCountRange getOperandCountRange() {
        return SqlOperandCountRanges.of(2);
    }

    /**
     * Makes sure that the number and types of arguments are allowable.
     * Operators (such as "ROW" and "AS") which do not check their arguments can
     * override this method.
     */
    public boolean checkOperandTypes(
            SqlCallBinding callBinding,
            boolean throwOnFailure) {
        final SqlNode left = callBinding.operand(0);
        final SqlNode right = callBinding.operand(1);
        if (SqlUtil.isNullLiteral(left, false)
                || left instanceof SqlDynamicParam) {
            return true;
        }
        RelDataType validatedNodeType =
                callBinding.getValidator().getValidatedNodeType(left);
        RelDataType returnType = SqlTypeUtil.deriveType(callBinding, right);
        if (!SqlTypeUtil.canCastFrom(returnType, validatedNodeType, true)) {
            if (throwOnFailure) {
                throw callBinding.newError(
                        RESOURCE.cannotCastValue(validatedNodeType.toString(),
                                returnType.toString()));
            }
            return false;
        }
        if (SqlTypeUtil.areCharacterSetsMismatched(
                validatedNodeType,
                returnType)) {
            if (throwOnFailure) {
                // Include full type string to indicate character
                // set mismatch.
                throw callBinding.newError(
                        RESOURCE.cannotCastValue(validatedNodeType.getFullTypeString(),
                                returnType.getFullTypeString()));
            }
            return false;
        }
        return true;
    }

    public SqlSyntax getSyntax() {
        return SqlSyntax.SPECIAL;
    }

    public void unparse(
            SqlWriter writer,
            SqlCall call,
            int leftPrec,
            int rightPrec) {
        assert call.operandCount() == 2;

        call.operand(0).unparse(writer, 0, 0);

//        final SqlWriter.Frame frame = writer.startFunCall(getName());
//        call.operand(0).unparse(writer, 0, 0);
//        writer.sep("AS");
//        if (call.operand(1) instanceof SqlIntervalQualifier) {
//            writer.sep("INTERVAL");
//        }
//
//        SqlNode operand = call.operand(1);
//        if (operand instanceof SqlDataTypeSpec) {
//            SqlDataTypeSpec operand1 = (SqlDataTypeSpec) operand;
//            SqlIdentifier typeName = operand1.getTypeName();
//            operand = convertTypeToSpec(SqlTypeName.valueOf(Util.last(typeName.names)));
//            operand.unparse(writer, 0, 0);
//            writer.endFunCall(frame);
//            return;
//        }
//        operand.unparse(writer, 0, 0);
//        writer.endFunCall(frame);
    }

    @Override
    public SqlMonotonicity getMonotonicity(SqlOperatorBinding call) {
        final RelDataType castFromType = call.getOperandType(0);
        final RelDataTypeFamily castFromFamily = castFromType.getFamily();
        final Collator castFromCollator = castFromType.getCollation() == null
                ? null
                : castFromType.getCollation().getCollator();
        final RelDataType castToType = call.getOperandType(1);
        final RelDataTypeFamily castToFamily = castToType.getFamily();
        final Collator castToCollator = castToType.getCollation() == null
                ? null
                : castToType.getCollation().getCollator();
        if (!Objects.equals(castFromCollator, castToCollator)) {
            // Cast between types compared with different collators: not monotonic.
            return SqlMonotonicity.NOT_MONOTONIC;
        } else if (castFromFamily instanceof SqlTypeFamily
                && castToFamily instanceof SqlTypeFamily
                && nonMonotonicCasts.containsEntry(castFromFamily, castToFamily)) {
            return SqlMonotonicity.NOT_MONOTONIC;
        } else {
            return call.getOperandMonotonicity(0);
        }
    }

    public static SqlDataTypeSpec convertTypeToSpec(RelDataType type,
                                                    String charSetName, int maxPrecision) {
        SqlTypeName typeName = type.getSqlTypeName();
        return convertTypeToSpec(typeName);
    }

    @NotNull
    private static SqlDataTypeSpec convertTypeToSpec(SqlTypeName typeName) {
        String mysqlType = "CHAR";
        switch (typeName) {
            case BOOLEAN:
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
                mysqlType = "SIGNED";
                break;
            case REAL:
            case DOUBLE:
            case FLOAT:
            case DECIMAL:
                mysqlType = "decimal";
                break;
            case DATE:
                mysqlType = "date";
                break;
            case TIME_WITH_LOCAL_TIME_ZONE:
            case TIME:
                mysqlType = "time";
                break;
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
            case TIMESTAMP:
                mysqlType = "datetime";
                break;
            case BINARY:
            case VARBINARY:
                mysqlType = "binary";
                break;
            case CHAR:
            case VARCHAR:
                mysqlType = "CHAR";
                break;
            case INTERVAL_YEAR:
            case INTERVAL_YEAR_MONTH:
            case INTERVAL_MONTH:
            case INTERVAL_DAY:
            case INTERVAL_DAY_HOUR:
            case INTERVAL_DAY_MINUTE:
            case INTERVAL_DAY_SECOND:
            case INTERVAL_HOUR:
            case INTERVAL_HOUR_MINUTE:
            case INTERVAL_HOUR_SECOND:
            case INTERVAL_MINUTE:
            case INTERVAL_MINUTE_SECOND:
            case INTERVAL_SECOND:
            case INTERVAL_MICROSECOND:
            case INTERVAL_WEEK:
            case INTERVAL_QUARTER:
            case INTERVAL_SECOND_MICROSECOND:
            case INTERVAL_MINUTE_MICROSECOND:
            case INTERVAL_HOUR_MICROSECOND:
            case INTERVAL_DAY_MICROSECOND:
                mysqlType = "time";
                break;
            case NULL:
            case ANY:
            case SYMBOL:
            case MULTISET:
            case ARRAY:
            case MAP:
            case DISTINCT:
            case STRUCTURED:
            case ROW:
            case OTHER:
            case CURSOR:
            case COLUMN_LIST:
            case DYNAMIC_STAR:
            case GEOMETRY:
            case SARG:
                mysqlType = "char";
                break;
        }
        return new SqlDataTypeSpec(new SqlUserDefinedTypeNameSpec(mysqlType, SqlParserPos.ZERO), SqlParserPos.ZERO);
    }
}
