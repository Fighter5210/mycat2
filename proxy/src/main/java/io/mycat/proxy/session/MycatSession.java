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
package io.mycat.proxy.session;

import io.mycat.MycatDataContext;
import io.mycat.MycatDataContextEnum;
import io.mycat.MycatException;
import io.mycat.MycatUser;
import io.mycat.beans.mysql.MySQLIsolation;
import io.mycat.beans.mysql.packet.MySQLPacket;
import io.mycat.beans.mysql.packet.MySQLPacketSplitter;
import io.mycat.beans.mysql.packet.PacketSplitterImpl;
import io.mycat.beans.mysql.packet.ProxyBuffer;
import io.mycat.buffer.BufferPool;
import io.mycat.buffer.HeapBufferPool;
import io.mycat.command.CommandDispatcher;
import io.mycat.command.LocalInFileRequestParseHelper.LocalInFileSession;
import io.mycat.config.MySQLServerCapabilityFlags;
import io.mycat.proxy.buffer.ProxyBufferImpl;
import io.mycat.proxy.handler.MySQLPacketExchanger;
import io.mycat.proxy.handler.MycatSessionWriteHandler;
import io.mycat.proxy.handler.NIOHandler;
import io.mycat.proxy.monitor.MycatMonitor;
import io.mycat.proxy.packet.FrontMySQLPacketResolver;
import io.mycat.proxy.reactor.MycatReactorThread;
import io.mycat.proxy.reactor.NIOJob;
import io.mycat.util.CharsetUtil;
import io.mycat.util.VertxUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.future.PromiseInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

//tcp.port in {8066} or tcp.port in  {3066}
public final class MycatSession extends AbstractSession<MycatSession> implements LocalInFileSession,
        MySQLProxyServerSession<MycatSession> {
    private final static Logger LOGGER = LoggerFactory.getLogger(MycatSession.class);
    private CommandDispatcher commandHandler;

    /**
     * mysql服务器状态
     */
    private final MycatDataContext dataContext;
    /***
     * 以下资源要做session关闭时候释放
     */
    private final ProxyBuffer proxyBuffer;//clearQueue
    private final ByteBuffer header = ByteBuffer.allocate(4);//gc
    private final ConcurrentLinkedQueue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();//buffer recycle
    //  private final MySQLPacketResolver packetResolver = new BackendMySQLPacketResolver(this);//clearQueue
    private final BufferPool crossSwapThreadBufferPool;


    /**
     * 报文写入辅助类
     */
    private final ByteBuffer[] packetContainer = new ByteBuffer[2];
    private final MySQLPacketSplitter packetSplitter = new PacketSplitterImpl();
    private volatile ProcessState processState;//每次在处理请求时候就需要重置
    private MySQLClientSession backend;//unbindSource
    private MycatSessionWriteHandler writeHandler = WriteHandler.INSTANCE;
    private final FrontMySQLPacketResolver frontResolver;
    private byte packetId = 0;
    private final ArrayDeque<NIOJob> delayedNioJobs = new ArrayDeque<>();


    public MycatSession(MycatDataContext dataContext, BufferPool bufferPool, NIOHandler nioHandler,
                        SessionManager<MycatSession> sessionManager) {
        super(dataContext.getSessionId(), nioHandler, sessionManager);
        HeapBufferPool heapBufferPool = new HeapBufferPool();
        this.proxyBuffer = new ProxyBufferImpl(heapBufferPool);
        this.crossSwapThreadBufferPool = bufferPool;

        this.processState = ProcessState.READY;
        this.frontResolver = new FrontMySQLPacketResolver(heapBufferPool, this);
        this.packetId = 0;
        this.dataContext = dataContext;
    }

    public void setCommandHandler(CommandDispatcher commandHandler) {
        this.commandHandler = commandHandler;
    }

    public CommandDispatcher getCommandHandler() {
        return commandHandler;
    }

    public void switchWriteHandler(MycatSessionWriteHandler writeHandler) {
        this.writeHandler = writeHandler;
    }

    public void onHandlerFinishedClear() {
        resetPacket();
        setResponseFinished(ProcessState.READY);
        this.change2ReadOpts();
    }

    public MySQLIsolation getIsolation() {
        return this.dataContext.getIsolation();
    }

    public void setIsolation(MySQLIsolation isolation) {
        LOGGER.info("set mycat session id:{} isolation:{}", sessionId(), isolation);
        this.dataContext.setIsolation(isolation);
    }


    public void setMultiStatementSupport(boolean on) {

    }

    public void setCharset(String charsetName) {
        setCharset(CharsetUtil.getIndex(charsetName), charsetName);
    }

    public boolean isBindMySQLSession() {
        return backend != null;
    }


    private void setCharset(int index, String charsetName) {
        this.dataContext.setCharset(index, charsetName, StandardCharsets.UTF_8);
    }

    public void setSchema(String schema) {
        this.dataContext.useShcema(schema);
    }

    public MySQLClientSession getMySQLSession() {
        return backend;
    }


    @Override
    public synchronized PromiseInternal<Void> close(boolean normal, String hint) {
        try {
            if (normal){
                dataContext.close();
            }else {
                dataContext.kill();
            }
        } catch (Exception e) {
            LOGGER.error("", e);
        }
        if (!normal) {
            assert hint != null;
            setLastMessage(hint);
        }
        assert hint != null;

        try {
            MySQLClientSession sqlSession = getMySQLSession();
            if (sqlSession != null) {
                sqlSession.close(false, hint);
            }
        } catch (Exception e) {
            LOGGER.error("", e);
        }
        resetPacket();
        if (this.getMySQLSession() != null) {
            this.getMySQLSession().close(normal, hint);
        }
        hasClosed = true;
        try {
            getSessionManager().removeSession(this, normal, hint);
        } catch (Exception e) {
            LOGGER.error("", e);
        }
        return VertxUtil.newSuccessPromise();
    }

    @Override
    public Future<Void> directWrite(Buffer buffer) {
        throw new UnsupportedOperationException();
    }


    @Override
    public Future<Void> directWriteEnd() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void directWriteStart() {
        throw new UnsupportedOperationException();
    }


    public ProxyBuffer currentProxyBuffer() {
        return proxyBuffer;
    }

    public int getServerCapabilities() {
        return this.dataContext.getServerCapabilities();
    }

    public void setServerCapabilities(int serverCapabilities) {
        this.dataContext.setServerCapabilities(serverCapabilities);
    }

    public void setMySQLSession(MySQLClientSession mySQLSession) {
        this.backend = mySQLSession;
    }

    public long getAffectedRows() {
        return this.dataContext.getAffectedRows();
    }

    public void setAffectedRows(long affectedRows) {
        this.dataContext.setAffectedRows(affectedRows);
    }

    @Override
    public BufferPool writeBufferPool() {
        return this.crossSwapThreadBufferPool;
    }

    @Override
    public ConcurrentLinkedQueue<ByteBuffer> writeQueue() {
        return writeQueue;
    }

    @Override
    public ByteBuffer packetHeaderBuffer() {
        return header;
    }


    @Override
    public ByteBuffer[] packetContainer() {
        return packetContainer;
    }

    @Override
    public void setPacketId(int packetId) {
        this.packetId = (byte) packetId;
    }

    @Override
    public byte getNextPacketId() {
        return ++packetId;
    }


    @Override
    public MySQLPacketSplitter packetSplitter() {
        return packetSplitter;
    }

    @Override
    public void switchProxyWriteHandler() {
        clearReadWriteOpts();
        this.writeHandler = MySQLPacketExchanger.WriteHandler.INSTANCE;
    }

    @Override
    public String getLastMessage() {
        String lastMessage = this.dataContext.getLastMessage();
        return " " + lastMessage + "";
    }

    @Override
    public String setLastMessage(String lastMessage) {
        this.dataContext.setLastMessage(lastMessage);
        return lastMessage;
    }

    @Override
    public long affectedRows() {
        return this.dataContext.getAffectedRows();
    }


    @Override
    public int getServerStatusValue() {
        return this.dataContext.serverStatus();
    }

    public void setInTranscation(boolean on) {
        this.dataContext.setInTransaction(on);
    }

    public void setLastInsertId(long s) {
        this.dataContext.setLastInsertId(s);
    }

    @Override
    public int getLastErrorCode() {
        return this.dataContext.getLastErrorCode();
    }

    @Override
    public boolean isDeprecateEOF() {
        return MySQLServerCapabilityFlags.isDeprecateEOF(this.dataContext.getServerCapabilities());
    }

    @Override
    public int getWarningCount() {
        return this.dataContext.getWarningCount();
    }

    @Override
    public long getLastInsertId() {
        return this.dataContext.getLastInsertId();
    }

    @Override
    public void resetSession() {
        throw new MycatException("unsupport!");
    }

    @Override
    public Charset charset() {
        return this.dataContext.getCharset();
    }


    @Override
    public int charsetIndex() {
        return this.dataContext.getCharsetIndex();
    }

    @Override
    public int getCapabilities() {
        return this.dataContext.getServerCapabilities();
    }

    @Override
    public void setLastErrorCode(int errorCode) {
        this.dataContext.setVariable(MycatDataContextEnum.LAST_ERROR_CODE, errorCode);
    }

    @Override
    public boolean isResponseFinished() {
        return processState == ProcessState.DONE;
    }

    @Override
    public void setResponseFinished(ProcessState b) {
        if (this.processState == ProcessState.DONE && b == ProcessState.DONE) {
            String error = "The response has ended, but there are still writes ...";
            LOGGER.error(error);
            throw new IllegalArgumentException(error);
        }
        this.processState = b;
    }

    @Override
    public void switchMySQLServerWriteHandler() {
        this.clearReadWriteOpts();
        this.writeHandler = WriteHandler.INSTANCE;
    }


    @Override
    public PromiseInternal<Void> writeToChannel() throws IOException {
        try {
            return writeHandler.writeToChannel(this);
        } catch (Exception e) {
            writeHandler.onException(this, e);
            resetPacket();
            throw e;
        }
    }

    @Override
    public final boolean readFromChannel() throws IOException {
        boolean b = frontResolver.readFromChannel();
        if (b) {
            MycatMonitor.onFrontRead(this, proxyBuffer.currentByteBuffer(),
                    proxyBuffer.channelReadStartIndex(), proxyBuffer.channelReadEndIndex());
        }
        return b;
    }

    public MySQLPacket currentProxyPayload() {
        return frontResolver.getPayload();
    }

    public void resetCurrentProxyPayload() {
        frontResolver.reset();
    }

    public void resetPacket() {
        resetCurrentProxyPayload();
        writeHandler.onClear(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MycatSession that = (MycatSession) o;

        return this.sessionId == that.sessionId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.sessionId);
    }

    @Override
    public boolean shouldHandleContentOfFilename() {
        Object variable = this.dataContext.getVariable(MycatDataContextEnum.IS_LOCAL_IN_FILE_REQUEST_STATE);
        return variable.equals(1);
    }

    @Override
    public void setHandleContentOfFilename(boolean need) {
        this.dataContext.setVariable(MycatDataContextEnum.IS_LOCAL_IN_FILE_REQUEST_STATE, need ? 1 : 0);
    }


    @Override
    public void switchNioHandler(NIOHandler nioHandler) {
        this.nioHandler = nioHandler;
    }

    public String getSchema() {
        return this.dataContext.getDefaultSchema();
    }

    public void useSchema(String schema) {
        this.dataContext.useShcema(schema);
    }

    public MycatUser getUser() {
        return dataContext.getUser();
    }


    public void setCharset(int index) {
        this.setCharset(CharsetUtil.getCharset(index));
    }

    public MycatSessionWriteHandler getWriteHandler() {
        return writeHandler;
    }

    public FrontMySQLPacketResolver getMySQLPacketResolver() {
        return frontResolver;
    }

    public ProcessState getProcessState() {
        return processState;
    }


    public void setAutoCommit(boolean autocommit) {
        this.dataContext.setAutoCommit(autocommit);
    }

    public boolean isInTransaction() {
        return dataContext.isInTransaction();
    }

    public boolean isAutocommit() {
        return dataContext.isAutocommit();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        if (iface == MycatDataContext.class) {
            return (T) dataContext;
        }
        return null;
    }

    public MycatDataContext getDataContext() {
        return dataContext;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return unwrap(iface) != null;
    }

    public boolean isIOThreadMode() {
        return Thread.currentThread() == this.getIOThread();
    }

    public void addDelayedNioJob(NIOJob runnable) {
        Objects.requireNonNull(runnable);
        delayedNioJobs.addLast(runnable);
    }

    public void runDelayedNioJob() {
        MycatReactorThread ioThread = getIOThread();
        while (!delayedNioJobs.isEmpty()) {
            ioThread.addNIOJob(delayedNioJobs.pollFirst());
        }
    }
}
