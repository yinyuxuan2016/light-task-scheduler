package com.lts.spring.tasktracker;

import com.lts.core.domain.Job;
import com.lts.tasktracker.Result;
import com.lts.tasktracker.runner.JobContext;
import com.lts.tasktracker.runner.JobRunner;

import java.lang.reflect.Method;

/**
 * @author Robert HG (254963746@qq.com) on 4/2/16.
 */
class JobRunnerBuilder {

    public static JobRunner build(final Object targetObject, final Method targetMethod, final Class<?>[] pTypes) {

        return new JobRunner() {
            @Override
            public Result run(JobContext jobContext) throws Throwable {
                if (pTypes == null || pTypes.length == 0) {
                    return (Result) targetMethod.invoke(targetObject);
                }
                Object[] pTypeValues = new Object[pTypes.length];

                for (int i = 0; i < pTypes.length; i++) {
                    if (pTypes[i] == Job.class) {
                        pTypeValues[i] = jobContext.getJob();
                    } else if (pTypes[i] == JobContext.class) {
                        pTypeValues[i] = jobContext;
                    } else {
                        pTypeValues[i] = null;
                    }
                }
                return (Result) targetMethod.invoke(targetObject, pTypeValues);
            }
        };
    }
}
