package ru.gb.simplenas.common.annotations;

import ru.gb.simplenas.common.structs.OperationCodes;

import java.lang.annotation.*;

@Retention (RetentionPolicy.RUNTIME)
@Target (ElementType.METHOD)
public @interface ManipulateMethod {

    OperationCodes[] opcodes();
}
