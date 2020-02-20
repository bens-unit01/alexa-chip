#!/bin/bash

# Inspired by https://gist.github.com/alobato/1968852

### BEGIN INIT INFO
# Provides:          AlexaPi
# Required-Start:    $all
# Required-Stop:     $all
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: AlexaPi Service
# Description:       Start / Stop AlexaPi Service
### END INIT INFO

set -e

NAME="AlexaPi"
PIDFILE="/run/$NAME/$NAME.pid"
DAEMON="/home/chip/alexa-startup.sh"
DAEMON_OPTS=""
RUN_USER="chip"
RUN_GROUP="chip"

#MONITOR_ENABLEFILE="/etc/opt/AlexaPi/monitor_enable"
#MONITOR_PIDFILE="/run/${NAME}/monitor.pid"
#MONITOR_DAEMON="/opt/AlexaPi/src/scripts/monitorAlexa.sh"

function alexa_run {
    mkdir -p /run/$NAME
    chown $RUN_USER:$RUN_GROUP /run/$NAME
    start-stop-daemon --start --background --quiet --chuid $RUN_USER:$RUN_GROUP --chdir /run/$NAME --pidfile $PIDFILE --make-pidfile --exec $DAEMON -- $DAEMON_OPTS
}

#exec > /var/log/$NAME.log 2>&1

case "$1" in

    start)
        echo -n "Starting $NAME ... " >> /home/chip/boot.txt
#        alexa_run

#        if [ -f $MONITOR_ENABLEFILE ]; then
#            start-stop-daemon --start --background --quiet --pidfile $MONITOR_PIDFILE --make-pidfile --exec $MONITOR_DAEMON
#        fi

        echo "done." >> /home/chip/boot.txt
    ;;

    silent)
        echo -n "Starting $NAME in silent mode ... " >> /home/chip/boot.txt
        DAEMON_OPTS="$DAEMON_OPTS -s"
#        alexa_run
        echo "done." >> /home/chip/boot.txt

    ;;

    stop)
        echo -n "Stopping $NAME ... " >> /home/chip/boot.txt
#        start-stop-daemon --stop --quiet --oknodo --pidfile $MONITOR_PIDFILE --remove-pidfile
#       start-stop-daemon --stop --quiet --oknodo --pidfile $PIDFILE --remove-pidfile
        echo "done." >> /home/chip/boot.txt
	;;

    restart|force-reload)
        echo -n "Restarting $NAME ... " >> /home/chip/boot.txt
        start-stop-daemon --stop --quiet  --oknodo --retry 30 --pidfile $PIDFILE --remove-pidfile
        alexa_run
        echo "done." >> /home/chip/boot.txt
    ;;
    *)
        echo "Usage: $0 {start|stop|restart}"
        exit 1

esac

exit 0
