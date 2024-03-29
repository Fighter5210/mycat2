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
package io.mycat;

import io.mycat.calcite.CodeExecuterContext;
import io.mycat.config.MycatServerConfig;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.Response;
import okhttp3.*;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.runtime.NewMycatDataContext;
import org.apache.calcite.schema.SchemaPlus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
public abstract class NewMycatDataContextImpl implements NewMycatDataContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewMycatDataContextImpl.class);
    protected final MycatDataContext context;
    protected final CodeExecuterContext codeExecuterContext;
    protected final DrdsSqlWithParams drdsSqlWithParams;

    public NewMycatDataContextImpl(MycatDataContext dataContext,
                                   CodeExecuterContext context,
                                   DrdsSqlWithParams drdsSqlWithParams) {
        this.context = dataContext;
        this.codeExecuterContext = context;
        this.drdsSqlWithParams = drdsSqlWithParams;
    }

    @Override
    public SchemaPlus getRootSchema() {
        return null;
    }

    @Override
    public JavaTypeFactory getTypeFactory() {
        return null;
    }

    @Override
    public QueryProvider getQueryProvider() {
        return null;
    }

    @Override
    public Object get(String name) {
        if (name.startsWith("?")) {
            int index = Integer.parseInt(name.substring(1));
            return drdsSqlWithParams.getParams().get(index);
        }
        return codeExecuterContext.getVarContext().get(name);
    }

    public Object getSessionVariable(String name) {
        return context.getVariable(false, name);
    }

    public Object getGlobalVariable(String name) {
        return context.getVariable(true, name);
    }

    public String getDatabase() {
        return context.getDefaultSchema();
    }

    public Long getLastInsertId() {
        return context.getLastInsertId();
    }

    public Long getConnectionId() {
        return context.getSessionId();
    }

    public Object getUserVariable(String name) {
        return null;
    }

    public String getCurrentUser() {
        MycatUser user = context.getUser();
        Authenticator authenticator = MetaClusterCurrent.wrapper(Authenticator.class);
        return user.getUserName() + "@" + authenticator.getUserInfo(user.getUserName()).getIp();
    }

    public String getUser() {
        MycatUser user = context.getUser();
        return user.getUserName() + "@" + user.getHost();
    }

    @Override
    public DrdsSqlWithParams getDrdsSql() {
        return drdsSqlWithParams;
    }

    @Override
    public Long getRowCount() {
        return context.getAffectedRows();
    }

    public MycatDataContext getContext() {
        return context;
    }


    public Integer getLock(String name, int timeout) {
        MycatDataContext context = getContext();
        return context.getLock(name, timeout);
    }

    public Integer releaseLock(String name) {
        MycatDataContext context = getContext();
        return context.releaseLock(name);
    }

    public Integer isFreeLock(String name) {
        MycatDataContext context = getContext();
        return context.isFreeLock(name);
    }
}
