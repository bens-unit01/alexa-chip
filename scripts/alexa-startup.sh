#!/bin/bash
echo "hello systemd"
echo "alexa app started " $(date) $VLC_PLUGIN_PATH >> /home/chip/boot.txt

cd /home/chip/alexa-testing/alexa-avs-sample-app-raspberry-pi/samples/javaclient
./run  &
cd ../wakeWordAgent
./run 
#touch  /home/chip/boot2.txt
#chown chip:chip /home/chip/boot2.txt
#echo "alexa-startup script" >> /home/chip/boot2.txt 

