package fr.umlv.smalljs.astinterp;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.ast.Visitor;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import fr.umlv.smalljs.rt.JSObject.Invoker;

public class ASTInterpreter {
    private static <T> T as(Object value, Class<T> type, Expr failedExpr) {
        try {
            return type.cast(value);
        } catch(@SuppressWarnings("unused") ClassCastException e) {
            throw new Failure("at line " + failedExpr.lineNumber() + ", type error " + value + " is not a " + type.getSimpleName());
        }
    }

    static Object visit(Expr expr, JSObject env) {
        return VISITOR.visit(expr, env);
    }

    private static final Visitor<JSObject, Object> VISITOR =
            new Visitor<JSObject, Object>()
                    .when(Block.class, (block, env) -> {
                        //throw new UnsupportedOperationException("TODO Block");
                        for(var instr: block.instrs()) {
                            visit(instr, env);
                        }
                        return UNDEFINED;
                    })
                    .when(Literal.class, (literal, env) -> {
                        //throw new UnsupportedOperationException("TODO Literal");
                        return literal.value();
                    })
                    
                    .when(FunCall.class, (funCall, env) -> {
                        var value = visit(funCall.qualifier(), env);
                        var function = as(value, JSObject.class, funCall);
                        var arguments = funCall.args().stream().map(arg-> visit(arg, env)).toArray();
                        return function.invoke(UNDEFINED, arguments);
                        /*
                        Mauvaise version
                        if(funCall.qualifier() instanceof Literal<?> literal && literal.value().equals("print")) {
                            var result = env.lookup(localVarAccess.name());
                            var print = as(result, JSObject.class, funCall);

                            var arguments = funCall.args().stream().map(arg-> visit(arg, env)).toArray(); // bad !
                            print.invoke(null, arguments);
                        }*/

                        // throw new UnsupportedOperationException("TODO FunCall");
                    })
                    .when(LocalVarAccess.class, (localVarAccess, env) -> {
                        var value = env.lookup(localVarAccess.name());
                        return value;
                        // throw new UnsupportedOperationException("TODO LocalVarAccess");
                    })
                    .when(LocalVarAssignment.class, (localVarAssignment, env) -> {
                        var research = env.lookup(localVarAssignment.name());
                        if(!localVarAssignment.declaration() && research == UNDEFINED) {
                            throw new Failure("no variable " + localVarAssignment.name() + " defined");
                        }
                        if(localVarAssignment.declaration() && research != UNDEFINED) {
                            throw new Failure("no variable " + localVarAssignment.name() + " already defined");
                        }
                        var value = visit(localVarAssignment.expr(), env);
                        env.register(localVarAssignment.name(), value);

                        return UNDEFINED;
                        //throw new UnsupportedOperationException("TODO LocalVarAssignment");
                    }) 
                    .when(Fun.class, (fun, env) -> {
                        var functionName = fun.name().orElse("lambda");
                        var invoker = new Invoker() {
                            @Override
                            public Object invoke(JSObject self, Object receiver, Object... args) {
                                if(fun.parameters().size() != args.length) {
                                    throw new Failure("wrong number of arguments at " + fun.lineNumber());
                                }
                                var parentEnv = JSObject.newEnv(env);
                                parentEnv.register("this", receiver);
                                for(var i = 0; i < fun.parameters().size(); i++) {
                                    parentEnv.register(fun.parameters().get(i), args[i]);
                                }
                                try {
                                    var result = visit(fun.body(), parentEnv);
                                    return result;
                                }
                                catch (ReturnError error){
                                    return error.getValue();
                                }
                            }
                        };
                        var function = JSObject.newFunction(functionName, invoker);
                        fun.name().ifPresent(name -> env.register(name, function));

                        return function;
                        // throw new UnsupportedOperationException("TODO Fun");
                    })
                    .when(Return.class, (_return, env) -> {
                        var value = visit(_return.expr(), env);
                        throw new ReturnError(value);
                        //throw new UnsupportedOperationException("TODO Return");
                    })
                    .when(If.class, (_if, env) -> {
                        var value = visit(_if.condition(), env);
                        if(!value.equals(0)) {
                            return visit(_if.trueBlock(), env);
                        }
                        return visit(_if.falseBlock(), env);

                        //throw new UnsupportedOperationException("TODO If");
                    })
                    .when(New.class, (_new, env) -> {
                        var object = JSObject.newObject(null);
                        _new.initMap().forEach((property, init) -> {
                            var value = visit(init, env);
                            object.register(property, value);
                        });
                        return object;
                        //throw new UnsupportedOperationException("TODO New");
                    })
                    .when(FieldAccess.class, (fieldAccess, env) -> {
                        //var value = visit(fieldAccess.receiver(), env);
                        throw new UnsupportedOperationException("TODO FieldAccess");
                    })
                    .when(FieldAssignment.class, (fieldAssignment, env) -> {
                        throw new UnsupportedOperationException("TODO FieldAssignment");
                    })
                    .when(MethodCall.class, (methodCall, env) -> {
                        throw new UnsupportedOperationException("TODO MethodCall");
                    })
            ;

    @SuppressWarnings("unchecked")
    public static void interpret(Script script, PrintStream outStream) {
        JSObject globalEnv = JSObject.newEnv(null);
        Block body = script.body();
        globalEnv.register("global", globalEnv);
        globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
            System.err.println("print called with " + Arrays.toString(args));
            outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
            return UNDEFINED;
        }));
        globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
        globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
        globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
        globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
        globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));

        globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("<", JSObject.newFunction("<",   (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
        globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
        globalEnv.register(">", JSObject.newFunction(">",   (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
        globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
        visit(body, globalEnv);
    }
}

/**
 * Q1 :
 * Il part de la racine
 * env : toutes les variables déjà lu !
 *
 * Q2 :
 *
 * Q6 :
 * opérateur + fonctionne car elle appelle une funcall (qu'on a déjà implementé)
 *
 * funcall : print
 * funcall : +
 * literal : 3 - 2
 *
 */
