#!/bin/sh
tmux new -s tail_log -d
tmux split-window -v -t tail_log
#tmux send-keys -t tail_log:0.0 'ls -al' C-m
#tmux send-keys -t tail_log:0.1 'ls' C-m

tmux send-keys -t tail_log:0.0 'l1' C-m
tmux send-keys -t tail_log:0.1 'l2' C-m
tmux attach -t tail_log
