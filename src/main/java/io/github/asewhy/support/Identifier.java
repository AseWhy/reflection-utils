package io.github.asewhy.support;

import java.lang.annotation.*;

/**
 * В случае если сущность запроса будет состоять как член какой-либо коллекции, то эта аннотация указывает на идентификатор сущности
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Identifier {

}
