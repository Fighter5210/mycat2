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
package io.mycat.sqlhandler.dql;

import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlShowCreateFunctionStatement;
import io.mycat.MycatDataContext;
import io.mycat.Response;
import io.mycat.beans.mysql.packet.ColumnDefPacket;
import io.mycat.prototypeserver.mysql.MySQLResultSet;
import io.mycat.prototypeserver.mysql.PrototypeService;
import io.mycat.sqlhandler.AbstractSQLHandler;
import io.mycat.sqlhandler.SQLRequest;
import io.vertx.core.Future;

import java.util.Collections;
import java.util.List;


public class ShowCreateFunctionHanlder extends AbstractSQLHandler<com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlShowCreateFunctionStatement> {

    @Override
    protected Future<Void> onExecute(SQLRequest<MySqlShowCreateFunctionStatement> request, MycatDataContext dataContext, Response response) {
        List<ColumnDefPacket> columnDefPacketList = PrototypeService. getShowCreateFunctionColumns();
        MySQLResultSet mySQLResultSet = MySQLResultSet.create(columnDefPacketList);
        mySQLResultSet.setRows(Collections.emptyList());
        return response.sendResultSet(mySQLResultSet.build());
    }
}
