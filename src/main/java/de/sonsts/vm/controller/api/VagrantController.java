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

package de.sonsts.vm.controller.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import de.sonsts.common.RunState;
import de.sonsts.vm.controller.process.ISuccessChecker;
import de.sonsts.vm.controller.process.ProcessStatus;
import de.sonsts.vm.controller.process.impl.OutputReader;

public class VagrantController
{
    private static final Logger LOGGER = Logger.getLogger(VagrantController.class.getName());
    private List<String> mStartCommand;
    private List<String> mStopCommand;
    private List<String> mDestroyCommand;
    private RunState mState = RunState.STOPPED;
    private String mVagrantDir;
    
    public VagrantController(String vagrantDir)
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER");
        }
        
        mVagrantDir = vagrantDir;
        
        mStartCommand = new ArrayList<String>();
        mStartCommand.add("vagrant");
        mStartCommand.add("up");
        
        mStopCommand = new ArrayList<String>();
        mStopCommand.add("vagrant");
        mStopCommand.add("halt");
        
        mDestroyCommand = new ArrayList<String>();
        mDestroyCommand.add("vagrant");
        mDestroyCommand.add("destroy");
        mDestroyCommand.add("-f");
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE");
        }
    }
    
    public RunState setState(RunState nextState)
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER nextState=" + nextState);
        }
        
        RunState retVal = RunState.UNKNOWN;
        switch (nextState)
        {
            case RUNNING:
                retVal = startVm();
                break;
            case STOPPED:
                retVal = stopVm();
                break;
            case DESTROYED:
                retVal = destroyVm();
                break;
            default:
                break;
        }
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
    
    private ProcessStatus runProcess(List<String> command, ISuccessChecker successChecker)
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER command=" + command + ", successChecker=" + successChecker);
        }
        
        ProcessStatus retVal = ProcessStatus.UNKNOWN;
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(mVagrantDir));
        
        Process process = null;
        try
        {
            process = processBuilder.start();
        }
        catch (IOException e)
        {
            LOGGER.error("Startup of process failed." + e);
        }
        
        if (null != process)
        {
            OutputReader outputReader = new OutputReader(process.getInputStream(), successChecker, process);
            Thread thread = new Thread(outputReader, outputReader.getClass().getSimpleName());
            thread.start();
            
            retVal = outputReader.awaitSuccess(TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES));
            if (!ProcessStatus.SUCCESS.equals(retVal))
            {
                LOGGER.error("Startup of process failed. (retVal=" + retVal + ")");
            }
        }
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
    
    private RunState startVm()
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER");
        }
        
        RunState retVal = mState;
        
        ProcessStatus processStatus = ProcessStatus.UNKNOWN;
        
        if (RunState.STOPPED.equals(mState))
        {
            processStatus = runProcess(mStartCommand, new ISuccessChecker()
            {
                public ProcessStatus check(String line, Integer exitValue)
                {
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("ENTER line=" + line + ", exitValue=" + exitValue);
                    }
                    
                    ProcessStatus retVal = ProcessStatus.UNKNOWN;
                    if (((null != exitValue) && (exitValue == 0))
                        || line.contains("Machine booted and ready!")
                        || line.contains("Complete!")
                        || line.contains("Machine already provisioned."))
                    {
                        retVal = ProcessStatus.SUCCESS;
                    }
                    else if (((null != exitValue) && (exitValue != 0)))
                    {
                        retVal = ProcessStatus.FAILED;
                    }
                    
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("LEAVE retVal=" + retVal);
                    }
                    
                    return retVal;
                }
            });
            
            mState = (ProcessStatus.SUCCESS.equals(processStatus)) ? RunState.RUNNING : mState;
        }
        
        retVal = mState;
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
    
    private RunState stopVm()
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER");
        }
        
        RunState retVal = mState;
        
        ProcessStatus processStatus = ProcessStatus.UNKNOWN;
        
        if (RunState.RUNNING.equals(mState))
        {
            processStatus = runProcess(mStopCommand, new ISuccessChecker()
            {
                public ProcessStatus check(String line, Integer exitValue)
                {
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("ENTER line=" + line + ", exitValue=" + exitValue);
                    }
                    
                    ProcessStatus retVal = ProcessStatus.UNKNOWN;
                    if (((null != exitValue) && (exitValue == 0)) ||
                        line.contains("Attempting graceful shutdown of VM..."))
                    {
                        retVal = ProcessStatus.SUCCESS;
                    }
                    
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("LEAVE retVal=" + retVal);
                    }
                    
                    return retVal;
                }
            });
            
            mState = (ProcessStatus.SUCCESS.equals(processStatus)) ? RunState.STOPPED : mState;
        }
        
        retVal = mState;
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
    
    private RunState destroyVm()
    {
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("ENTER");
        }
        
        RunState retVal = mState;
        
        ProcessStatus processStatus = ProcessStatus.UNKNOWN;
        
        if (RunState.RUNNING.equals(mState) || RunState.STOPPED.equals(mState))
        {
            processStatus = runProcess(mDestroyCommand, new ISuccessChecker()
            {
                public ProcessStatus check(String line, Integer exitValue)
                {
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("ENTER line=" + line + ", exitValue=" + exitValue);
                    }
                    
                    ProcessStatus retVal = ProcessStatus.UNKNOWN;
                    if (((null != exitValue) && (exitValue == 0))
                        || line.contains("VM not created.")
                        || line.contains("Destroying VM and associated drives"))
                    {
                        retVal = ProcessStatus.SUCCESS;
                    }
                    else if (((null != exitValue) && (exitValue != 0)))
                    {
                        retVal = ProcessStatus.SUCCESS;
                    }
                    
                    if (LOGGER.isTraceEnabled())
                    {
                        LOGGER.trace("LEAVE retVal=" + retVal);
                    }
                    
                    return retVal;
                }
            });
            
            mState = (ProcessStatus.SUCCESS.equals(processStatus)) ? RunState.DESTROYED : mState;
        }
        
        retVal = mState;
        
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("LEAVE retVal=" + retVal);
        }
        
        return retVal;
    }
}