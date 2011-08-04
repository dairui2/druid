/*
 * Copyright 1999-2011 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.pool;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import com.alibaba.druid.logging.Log;
import com.alibaba.druid.logging.LogFactory;
import com.alibaba.druid.pool.PoolablePreparedStatement.PreparedStatementKey;
import com.alibaba.druid.pool.PreparedStatementPool.MethodType;

/**
 * @author wenshao<szujobs@hotmail.com>
 */
public class PoolableConnection implements PooledConnection, Connection {

    private final static Log   LOG = LogFactory.getLog(PoolableConnection.class);

    protected Connection       conn;
    protected ConnectionHolder holder;

    public PoolableConnection(ConnectionHolder holder){
        this.conn = holder.getConnection();
        this.holder = holder;
    }

    protected SQLException handleException(Throwable t) throws SQLException {
        final ConnectionHolder holder = this.holder;

        //
        if (holder != null) {
            DruidAbstractDataSource dataSource = holder.getDataSource();
            if (dataSource != null) {
                holder.getDataSource().handleException(t);
            }
        }

        if (t instanceof SQLException) {
            SQLException sqlEx = (SQLException) t;

            // broadcastConnectionError
            ConnectionEvent event = new ConnectionEvent(this, sqlEx);
            for (ConnectionEventListener eventListener : holder.getConnectionEventListeners()) {
                eventListener.connectionErrorOccurred(event);
            }

            // exceptionSorter.isExceptionFatal
            if (holder != null) {
                DruidAbstractDataSource dataSource = holder.getDataSource();

                if (dataSource != null) {
                    ExceptionSorter exceptionSorter = dataSource.getExceptionSoter();
                    if (exceptionSorter != null && exceptionSorter.isExceptionFatal(sqlEx)) {
                        this.holder = null; // isClosed() is true
                    }
                }
            }

            throw sqlEx;
        } else {
            throw new SQLException("Error", t);
        }
    }

    void closePoolableStatement(PoolablePreparedStatement stmt) throws SQLException {
        PreparedStatement rawStatement = stmt.getRawPreparedStatement();

        rawStatement.clearParameters();

        if (holder.isPoolPreparedStatements()) {
            this.holder.getStatementPool().put(stmt);
            stmt.clearResultSet();
            holder.removeTrace(stmt);
            stmt.setClosed(true); // soft set close
        } else {
            stmt.closeInternal();
        }
    }

    public ConnectionHolder getConnectionHolder() {
        return holder;
    }

    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        if (holder == null) {
            LOG.warn("dup close");
            return;
        }

        DruidAbstractDataSource dataSource = holder.getDataSource();
        if (dataSource.isRemoveAbandoned()) {
            dataSource.removeActiveConnection(this);
        }

        for (ConnectionEventListener listener : holder.getConnectionEventListeners()) {
            listener.connectionClosed(new ConnectionEvent(this));
        }

        PreparedStatementPool statementPool = holder.getStatementPool();
        if (statementPool != null) {
            for (List<PoolablePreparedStatement> stmtList : statementPool.getMap().values()) {
                for (PoolablePreparedStatement stmt : stmtList) {
                    stmt.close();
                }
            }
        }

        holder.reset();
        holder.getDataSource().recycle(this);

        holder = null;
        conn = null;
    }

    // ////////////////////

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M1);

        if (holder.isPoolPreparedStatements()) {
            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
                                                                                                      throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M2);

        if (holder.isPoolPreparedStatements()) {

            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql, resultSetType, resultSetConcurrency);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M3);
        if (holder.isPoolPreparedStatements()) {

            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M4);
        if (holder.isPoolPreparedStatements()) {
            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql, columnIndexes);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M5);
        if (holder.isPoolPreparedStatements()) {
            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql, columnNames);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.M6);
        if (holder.isPoolPreparedStatements()) {
            PoolablePreparedStatement poolableStatement = holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        PreparedStatement stmt = conn.prepareStatement(sql, autoGeneratedKeys);

        holder.getDataSource().initStatement(stmt);

        PreparedStatement rtnVal = new PoolablePreparedStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    // ////////////////////

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.Precall_1);

        if (holder.isPoolPreparedStatements()) {
            PoolableCallableStatement poolableStatement = (PoolableCallableStatement) holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        CallableStatement stmt = conn.prepareCall(sql);

        holder.getDataSource().initStatement(stmt);

        CallableStatement rtnVal = new PoolableCallableStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.Precall_2);
        if (holder.isPoolPreparedStatements()) {
            PoolableCallableStatement poolableStatement = (PoolableCallableStatement) holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        CallableStatement stmt = conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);

        holder.getDataSource().initStatement(stmt);

        CallableStatement rtnVal = new PoolableCallableStatement(this, stmt, key);

        holder.addTrace(rtnVal);

        return rtnVal;
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();

        PreparedStatementKey key = new PreparedStatementKey(sql, getCatalog(), MethodType.Precall_3);
        if (holder.isPoolPreparedStatements()) {

            PoolableCallableStatement poolableStatement = (PoolableCallableStatement) holder.getStatementPool().get(key);

            if (poolableStatement != null) {
                poolableStatement.setPoolableConnection(this);
                poolableStatement.setClosed(false);
                holder.addTrace(poolableStatement);
                return poolableStatement;
            }
        }

        CallableStatement stmt = conn.prepareCall(sql, resultSetType, resultSetConcurrency);

        holder.getDataSource().initStatement(stmt);

        CallableStatement rtnVal = new PoolableCallableStatement(this, stmt, key);
        holder.addTrace(rtnVal);

        return rtnVal;
    }

    // ////////////////////

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();

        Statement stmt = conn.createStatement();

        holder.getDataSource().initStatement(stmt);

        PoolableStatement poolableStatement = new PoolableStatement(this, stmt);
        holder.addTrace(poolableStatement);

        return poolableStatement;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                                                                                                           throws SQLException {
        checkOpen();

        Statement stmt = conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);

        holder.getDataSource().initStatement(stmt);

        PoolableStatement poolableStatement = new PoolableStatement(this, stmt);
        holder.addTrace(poolableStatement);

        return poolableStatement;
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();

        Statement stmt = conn.createStatement(resultSetType, resultSetConcurrency);

        holder.getDataSource().initStatement(stmt);

        PoolableStatement poolableStatement = new PoolableStatement(this, stmt);
        holder.addTrace(poolableStatement);

        return poolableStatement;
    }

    // ////////////////////

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (conn == null) {
            return null;
        }
        return conn.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return conn.isWrapperFor(iface);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkOpen();

        return conn.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkOpen();

        try {
            conn.setAutoCommit(autoCommit);
        } catch (SQLException ex) {
            handleException(ex);
        }
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();

        return conn.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        checkOpen();

        try {
            conn.commit();
        } catch (SQLException ex) {
            handleException(ex);
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();

        try {
            conn.rollback();
        } catch (SQLException ex) {
            handleException(ex);
        } 
    }

    @Override
    public boolean isClosed() throws SQLException {
        if (holder == null) {
            return true;
        }

        return conn.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();

        return conn.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkOpen();

        conn.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkOpen();

        return conn.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkOpen();

        conn.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        checkOpen();

        return conn.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkOpen();

        conn.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkOpen();

        return conn.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();

        return conn.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();

        conn.clearWarnings();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkOpen();

        return conn.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkOpen();

        conn.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkOpen();

        conn.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        checkOpen();

        return conn.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkOpen();

        return conn.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkOpen();

        return conn.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkOpen();
        try {
            conn.rollback(savepoint);
        } catch (SQLException ex) {
            handleException(ex);
        }
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkOpen();
        try {
            conn.releaseSavepoint(savepoint);
        } catch (SQLException ex) {
            handleException(ex);
        }
    }

    @Override
    public Clob createClob() throws SQLException {
        checkOpen();

        return conn.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkOpen();

        return conn.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkOpen();

        return conn.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkOpen();

        return conn.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        checkOpen();

        return conn.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        if (holder == null) {
            throw new IllegalStateException();
        }

        conn.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        if (holder == null) {
            throw new IllegalStateException();
        }

        conn.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkOpen();

        return conn.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkOpen();

        return conn.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkOpen();

        return conn.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkOpen();

        return conn.createStruct(typeName, attributes);
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        if (holder == null) {
            throw new IllegalStateException();
        }

        holder.getConnectionEventListeners().add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        if (holder == null) {
            throw new IllegalStateException();
        }

        holder.getConnectionEventListeners().remove(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        if (holder == null) {
            throw new IllegalStateException();
        }

        holder.getStatementEventListeners().add(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        if (holder == null) {
            throw new IllegalStateException();
        }

        holder.getStatementEventListeners().remove(listener);
    }

    protected void checkOpen() throws SQLException {
        if (holder == null) {
            throw new SQLException("connection is closed");
        }
    }

    public String toString() {
        if (conn != null) {
            return conn.toString();
        } else {
            return "closed-conn-" + System.identityHashCode(this);
        }
    }

}
