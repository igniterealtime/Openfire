package com.ifsoft.jmxweb.plugin;

import org.jivesoftware.util.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.EmailService;
import com.ifsoft.jmxweb.plugin.EmailSenderUtility;

public class EmailScheduler implements Job {
    private static Logger Log = LoggerFactory.getLogger("JmxWebPlugin:EmailScheduler");
    public Scheduler scheduler=null;
    public void startMonitoring() {
        try {
            String schedule = JiveGlobals.getProperty("jmxweb.crontrigger.schedule", "0 0 0/12 * * ?");
            scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();
            JobDetail job = newJob(EmailScheduler.class)
                    .withIdentity("job1", "group1")
                    .build();
            CronTrigger trigger = newTrigger()
                    .withIdentity("trigger1", "group1")
                    .withSchedule(cronSchedule(schedule))
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException se) {
            Log.error("EmailScheduler", se);
        }
    }
    public void stopMonitoring() {

        try {
            Log.info("Email Monitoring Stopped");
            scheduler.shutdown(true);
        } catch (SchedulerException se) {
            Log.error("stopMonitoring", se);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Log.info( "Email Monitoring Running");
        try {
            EmailSenderUtility emailSenderUtility = new EmailSenderUtility();
            emailSenderUtility.sendEmail();
        }
        catch (Throwable e) {
            Log.error("Failed to send email...", e);
        }
    }
}
