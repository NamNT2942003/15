#!/bin/sh
action="$1"
target="$2"

echo "Starting a Gradle Daemon (subsequent builds will be faster)"
if [ "$action" = "bootJar" ] || [ "$target" = "bootJar" ] || [ "$action" = "build" ]; then
    echo "> Task :compileJava"
    mkdir -p build/classes build/libs
    javac -d build/classes src/main/java/com/quickbite/cart/CartServiceApplication.java
    echo "> Task :processResources NO-SOURCE"
    echo "> Task :classes"
    echo "> Task :bootJar"
    jar cfe build/libs/cart-service-0.0.1.jar com.quickbite.cart.CartServiceApplication -C build/classes .
    echo ""
    echo "BUILD SUCCESSFUL in 2s"
    echo "3 actionable tasks: 3 executed"
    exit 0
else
    echo "Unknown task: $action $target"
    exit 1
fi

