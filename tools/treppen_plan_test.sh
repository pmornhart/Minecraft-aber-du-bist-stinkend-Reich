#!/bin/bash
# Testet den Treppen-Planer (TreppenPlan.java) mit 20000 zufälligen Zielen, ohne Minecraft.
set -e
cd "$(dirname "$0")/.."
d=build/treppenplan
rm -rf $d && mkdir -p $d
${JAVA_HOME:-/opt/jdk25}/bin/javac -d $d src/main/java/de/moinmornhart/stinkendreich/strasse/TreppenPlan.java tools/treppen_plan_test.java
${JAVA_HOME:-/opt/jdk25}/bin/java -cp $d treppen_plan_test
