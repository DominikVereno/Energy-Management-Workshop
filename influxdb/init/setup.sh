#!/bin/sh
set -e

influx bucket create -n openems -o docs || true
