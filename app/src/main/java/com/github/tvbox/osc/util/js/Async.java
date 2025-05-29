package com.github.tvbox.osc.util.js;

import com.google.common.util.concurrent.SettableFuture;
import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

//import java9.util.concurrent.CompletableFuture;

public class Async {

    //private final CompletableFuture<Object> future;
    private final SettableFuture<Object> future;

    //public static CompletableFuture<Object> run(JSObject object, String name, Object[] args) {
    public static SettableFuture<Object> run(JSObject object, String name, Object[] args) {
        return new Async().call(object, name, args);
    }

    private Async() {
       // this.future = new CompletableFuture<>();
    //}

    //private CompletableFuture<Object> call(JSObject object, String name, Object[] args) {
     //   JSFunction function = object.getJSFunction(name);
     //   if (function == null) return empty();
     //   Object result = function.call(args);
     //   if (result instanceof JSObject) then(result);
     //   else future.complete(result);
     //   return future;
        this.future = SettableFuture.create();
    }

    //private CompletableFuture<Object> empty() {
        //future.complete(null);
    private SettableFuture<Object> call(JSObject object, String name, Object[] args) {
        try {
            JSFunction function = object.getJSFunction(name);
            if (function == null) {
                future.set(null);
                return future;
            }
            Object result = function.call(args);
            if (result instanceof JSObject) {
                then(result);
            } else {
                future.set(result);
            }
        } catch (Throwable t) {
            future.setException(t);
        }
        return future;
    }

    private void then(Object result) {
        JSObject promise = (JSObject) result;
        //JSFunction then = promise.getJSFunction("then");
        //if (then != null) then.call(callback);
        JSFunction thenFn = promise.getJSFunction("then");
        if (thenFn != null) {
            thenFn.call(callback);
        } else {
            // If there's no then, complete immediately
            future.set(result);
        }
    }

    private final JSCallFunction callback = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
           // future.complete(args[0]);
            // args[0] holds the resolved value from the JS promise
            future.set(args.length > 0 ? args[0] : null);
            return null;
        }
    };
}