package org.apache.commons.configuration.test;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.FileReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Stolen from Turbine
 *
 * @author <a href="mailto:hps@intermeta.de">Henning P. Schmiedehausen</a>
 * @version $Id: HsqlDB.java 439648 2006-09-02 20:42:10Z oheger $
 */

public class HsqlDB
{
    private Connection connection = null;
    private static Log log = LogFactory.getLog(HsqlDB.class);

    public HsqlDB(String uri, String databaseDriver, String loadFile)
            throws Exception
    {
        Class.forName(databaseDriver);

        this.connection = DriverManager.getConnection(uri, "sa", "");

            if (StringUtils.isNotEmpty(loadFile))
            {
                loadSqlFile(loadFile);
            }
        this.connection.commit();
    }

    public Connection getConnection()
    {
        return connection;
    }

    public void close()
    {
        try
        {
            connection.close();
        }
        catch (Exception e)
        {
        }
    }

    private void loadSqlFile(String fileName)
            throws Exception
    {
        Statement statement = null;
        try
        {
            statement = connection.createStatement();
            String commands = getFileContents(fileName);

            for (int targetPos = commands.indexOf(';'); targetPos > -1; targetPos = commands.indexOf(';'))
            {
                String cmd = commands.substring(0, targetPos + 1);
                try
                {
                    statement.execute(cmd);
                }
                catch (SQLException sqle)
                {
                    log.warn("Statement: " + cmd + ": " + sqle.getMessage());
                }
                
                commands = commands.substring(targetPos + 2);
            }
        }
        finally
        {
            if (statement != null)
            {
                statement.close();
            }
        }
    }

    private String getFileContents(String fileName)
            throws Exception
    {
        FileReader fr = new FileReader(fileName);

        char fileBuf[]  = new char[1024];
        StringBuffer sb = new StringBuffer(1000);
        int res = -1;

        while ((res = fr.read(fileBuf, 0, 1024)) > -1)
        {
            sb.append(fileBuf, 0, res);
        }
        fr.close();
        return sb.toString();
    }
}

