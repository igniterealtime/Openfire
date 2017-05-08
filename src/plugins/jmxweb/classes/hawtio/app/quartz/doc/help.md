### Quartz

The [Quartz](#/quartz/schedulers/) plugin in [hawtio](http://hawt.io "hawtio") offers functionality for viewing and managing Quartz Schedulers.

#### Quartz Tree ####

On the left hand side is the Quartz Tree which lists all the running Quartz schedulers in the JVM.

![Quartz Tree](app/quartz/doc/img/quartz-tree.png "Quartz Tree")

And on the main view area there is sub tabs to see details about the selected scheduler.

Clicking on a Quartz scheduler in the tree, selects it, and allows you to see more details about that particular scheduler.

#### Quartz Scheduler ####

This sub tab displays information about the scheduler, such as its name, version and state.

![Quartz Scheduler](app/quartz/doc/img/quartz-scheduler.png "Quartz Scheduler")

There is control buttons to suspend and resume the scheduler. When the scheduler is suspended (standby) then any
triggers will not be fired.

#### Quartz Triggers ####

The triggers sub tabs shows all the triggers for the selected scheduler in a table.

![Quartz Triggers](app/quartz/doc/img/quartz-triggers.png "Quartz Triggers")

Quartz has two kind of triggers:
* basic
* cron

The basic trigger is a trigger that can fire using a constant time interval, such as every 20 seconds.
The cron trigger is an advanced trigger that use cron expressions to define when the trigger should fire.

There is control buttons to suspend and resume trigger(s), and as well to edit the trigger as shown below:

![Quartz Edit Trigger](app/quartz/doc/img/quartz-edit-trigger.png "Quartz Edit Trigger")

#### Quartz Jobs ####

This sub tab shows all the jobs for the selected scheduler in a table.

![Quartz Jobs](app/quartz/doc/img/quartz-jobs.png "Quartz Jobs")

Clicking on the info button (<i class='icon-info'></i>) will display additional job information as shown below:

![Quartz View Job](app/quartz/doc/img/quartz-view-job.png "Quartz View Job")

