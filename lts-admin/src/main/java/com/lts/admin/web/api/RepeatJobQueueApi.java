package com.lts.admin.web.api;

import com.lts.admin.cluster.BackendAppContext;
import com.lts.admin.request.JobQueueReq;
import com.lts.admin.response.PaginationRsp;
import com.lts.admin.web.AbstractMVC;
import com.lts.admin.web.support.Builder;
import com.lts.admin.web.vo.RestfulResponse;
import com.lts.biz.logger.JobLogUtils;
import com.lts.biz.logger.domain.JobLogPo;
import com.lts.biz.logger.domain.LogType;
import com.lts.core.commons.utils.Assert;
import com.lts.core.commons.utils.StringUtils;
import com.lts.core.constant.Level;
import com.lts.core.support.JobDomainConverter;
import com.lts.core.support.JobUtils;
import com.lts.core.support.SystemClock;
import com.lts.queue.domain.JobPo;
import com.lts.store.jdbc.exception.DupEntryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * @author Robert HG (254963746@qq.com) on 3/26/16.
 */
@RestController
public class RepeatJobQueueApi extends AbstractMVC {

    @Autowired
    private BackendAppContext appContext;

    @RequestMapping("/job-queue/repeat-job-get")
    public RestfulResponse repeatJobGet(JobQueueReq request) {
        PaginationRsp<JobPo> paginationRsp = appContext.getRepeatJobQueue().pageSelect(request);
        RestfulResponse response = new RestfulResponse();
        response.setSuccess(true);
        response.setResults(paginationRsp.getResults());
        response.setRows(paginationRsp.getRows());
        return response;
    }

    @RequestMapping("/job-queue/repeat-job-update")
    public RestfulResponse repeatJobUpdate(JobQueueReq request) {
        // 检查参数
        try {
            Assert.hasLength(request.getJobId(), "jobId不能为空!");
            Assert.notNull(request.getRepeatInterval(), "repeatInterval不能为空!");
            Assert.isTrue(request.getRepeatInterval() > 0, "repeatInterval必须大于0");
            Assert.isTrue(request.getRepeatCount() >= -1, "repeatCount必须>= -1");
        } catch (IllegalArgumentException e) {
            return Builder.build(false, e.getMessage());
        }
        request.setCronExpression(null);
        JobPo jobPo = appContext.getRepeatJobQueue().getJob(request.getJobId());
        boolean success = appContext.getRepeatJobQueue().selectiveUpdateByJobId(request);
        if (success) {
            try {
                boolean relyOnPrevCycleChanged = !request.getRelyOnPrevCycle().equals(jobPo.getRelyOnPrevCycle());
                // repeatInterval变了或者repeatCount变少了
                boolean repeatIntervalChanged = !request.getRepeatInterval().equals(jobPo.getRepeatInterval());
                boolean repeatIntervalOrCountDecChanged = repeatIntervalChanged
                        || (
                        (jobPo.getRepeatCount() == -1 && request.getRepeatCount() > 0)
                                || (jobPo.getRepeatCount() != -1 && request.getRepeatCount() != -1 && request.getRepeatCount() < jobPo.getRepeatCount()
                        )
                );
                if (jobPo.getRelyOnPrevCycle() && !relyOnPrevCycleChanged) {
                    // 如果repeatInterval有修改,需要把triggerTime也要修改下
                    if (repeatIntervalChanged) {
                        long nextTriggerTime = JobUtils.getRepeatNextTriggerTime(jobPo);
                        request.setTriggerTime(new Date(nextTriggerTime));
                    }
                    // 把等待执行的队列也更新一下
                    appContext.getExecutableJobQueue().selectiveUpdateByJobId(request);
                } else {
                    // 2. 需要对批量任务做处理
                    if (relyOnPrevCycleChanged) {
                        if (jobPo.getRelyOnPrevCycle()) {
                            // 之前是依赖的,现在不依赖,需要生成批量任务
                            appContext.getExecutableJobQueue().remove(jobPo.getTaskTrackerNodeGroup(), jobPo.getJobId());
                            appContext.getNoRelyJobGenerator().generateCronJobForInterval(jobPo, new Date());
                        } else {
                            // 之前不依赖,现在依赖,需要删除批量任务
                            appContext.getExecutableJobQueue().removeBatch(jobPo.getRealTaskId(), jobPo.getTaskTrackerNodeGroup());
                            // 添加新的任务
                            jobPo.setTriggerTime(JobUtils.getRepeatNextTriggerTime(jobPo));
                            try {
                                appContext.getExecutableJobQueue().add(jobPo);
                            } catch (DupEntryException ignored) {
                            }
                        }
                    } else {
                        // 如果relyOnPrevCycle 没有修改过, 表示relyOnPrevCycle=false, 那么要看repeatIntervalOrCountDecChanged,如果修改过,需要删除重新生成
                        if (repeatIntervalOrCountDecChanged) {
                            appContext.getExecutableJobQueue().removeBatch(jobPo.getRealTaskId(), jobPo.getTaskTrackerNodeGroup());
                            appContext.getNoRelyJobGenerator().generateRepeatJobForInterval(jobPo, new Date());
                        } else {
                            appContext.getExecutableJobQueue().selectiveUpdateByTaskId(request);
                        }
                    }
                }

            } catch (Exception e) {
                return Builder.build(false, "更新等待执行的任务失败，请手动更新! error:" + e.getMessage());
            }
            JobLogUtils.log(LogType.UPDATE, jobPo, appContext.getJobLogger());
            return Builder.build(true);
        } else {
            return Builder.build(false, "该任务已经被删除或者执行完成");
        }
    }

    @RequestMapping("/job-queue/repeat-job-delete")
    public RestfulResponse repeatJobDelete(JobQueueReq request) {
        if (StringUtils.isEmpty(request.getJobId())) {
            return Builder.build(false, "JobId 必须传!");
        }
        JobPo jobPo = appContext.getRepeatJobQueue().getJob(request.getJobId());
        boolean success = appContext.getRepeatJobQueue().remove(request.getJobId());
        if (success) {
            try {
                appContext.getExecutableJobQueue().removeBatch(jobPo.getRealTaskId(), jobPo.getTaskTrackerNodeGroup());
//                appContext.getExecutableJobQueue().remove(request.getTaskTrackerNodeGroup(), request.getJobId());
            } catch (Exception e) {
                return Builder.build(false, "删除等待执行的任务失败，请手动删除! error:{}" + e.getMessage());
            }
        }
        JobLogUtils.log(LogType.DEL, jobPo, appContext.getJobLogger());
        return Builder.build(true);
    }

    @RequestMapping("/job-queue/repeat-job-suspend")
    public RestfulResponse repeatJobSuspend(JobQueueReq request) {
        if (StringUtils.isEmpty(request.getJobId())) {
            return Builder.build(false, "JobId 必须传!");
        }
        JobPo jobPo = appContext.getRepeatJobQueue().getJob(request.getJobId());
        if (jobPo == null) {
            return Builder.build(false, "任务不存在，或者已经删除");
        }
        try {
            jobPo.setGmtModified(SystemClock.now());
            appContext.getSuspendJobQueue().add(jobPo);
        } catch (DupEntryException e) {
            return Builder.build(false, "该任务已经被暂停, 请检查暂停队列");
        } catch (Exception e) {
            return Builder.build(false, "移动任务到暂停队列失败, error:" + e.getMessage());
        }
        try {
            appContext.getRepeatJobQueue().remove(request.getJobId());
        } catch (Exception e) {
            return Builder.build(false, "删除Repeat任务失败，请手动删除! error:" + e.getMessage());
        }
        try {
            if (!jobPo.getRelyOnPrevCycle()) {
                appContext.getRepeatJobQueue().updateLastGenerateTriggerTime(jobPo.getJobId(), new Date().getTime());
                appContext.getExecutableJobQueue().removeBatch(jobPo.getRealTaskId(), jobPo.getTaskTrackerNodeGroup());
            } else {
                appContext.getExecutableJobQueue().remove(request.getTaskTrackerNodeGroup(), request.getJobId());
            }
        } catch (Exception e) {
            return Builder.build(false, "删除等待执行的任务失败，请手动删除! error:" + e.getMessage());
        }

        JobLogUtils.log(LogType.SUSPEND, jobPo, appContext.getJobLogger());

        return Builder.build(true);
    }

}