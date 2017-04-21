/*
 * Copyright (c) 2017, Michael Sonst, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.sonsts.vm.controller.process.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

import de.sonsts.vm.controller.process.ISuccessChecker;
import de.sonsts.vm.controller.process.ProcessStatus;

public class OutputReader implements Runnable
{
    private static final Logger LOGGER = Logger.getLogger(OutputReader.class.getName());
    
    private BufferedReader mReader;
    private ISuccessChecker mSuccessChecker;
    private ProcessStatus mProcessStatus = ProcessStatus.UNKNOWN;
    
    private Process mProcess;
    
    public OutputReader(InputStream inputStream, ISuccessChecker successChecker, Process process)
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER inputStream=" + inputStream + ", successChecker=" + successChecker + ", process=" + process);
        }
        
        mReader = new BufferedReader(new InputStreamReader(inputStream));
        mSuccessChecker = successChecker;
        mProcess = process;
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE");
        }
    }
    
    public ProcessStatus awaitSuccess(long timeout)
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER timeout=" + timeout);
        }
        
        ProcessStatus retVal = mProcessStatus;
        
        boolean loop = true;
        long endTime = System.currentTimeMillis() + timeout;
        
        while (loop)
        {
            loop = (ProcessStatus.UNKNOWN == mProcessStatus);
            retVal = mProcessStatus;
            
            if (loop)
            {
                if (timeout > 0)
                {
                    loop = (System.currentTimeMillis() < endTime);
                    if (!loop)
                    {
                        retVal = ProcessStatus.TIMEOUT;
                        if (LOGGER.isTraceEnabled())
                        {
                            LOGGER.trace("Timeout");
                        }
                    }
                }
                
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    e = null;
                }
            }
        }
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
    
    public void run()
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER");
        }
        
        ProcessStatus status = mProcessStatus;
        try
        {
            String line = mReader.readLine();
            while ((line != null) && (ProcessStatus.UNKNOWN == status))
            {
                if (LOGGER.isTraceEnabled())
                {
                    LOGGER.trace("ENTER line=" + line);
                }
                
                Integer exitValue = null;
                
                try
                {
                    exitValue = mProcess.exitValue();
                }
                catch (IllegalThreadStateException e)
                {
                    e = null;
                }
                
                status = mSuccessChecker.check(line, exitValue);
                if ((ProcessStatus.UNKNOWN != status) && LOGGER.isTraceEnabled())
                {
                    LOGGER.trace("status=" + status);
                }
                
                if (ProcessStatus.UNKNOWN == status)
                {
                    line = mReader.readLine();
                }
            }
            mReader.close();
        }
        catch (IOException e)
        {
            LOGGER.trace("LEAVE Failed to read subprocess output" + e, e);
        }
        
        mProcessStatus = status;
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE");
        }
    }
}
