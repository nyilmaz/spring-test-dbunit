package com.github.springtestdbunit.annotation;

import java.lang.annotation.*;

/**
 * @author nyilmaz
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface DatabaseConnectionSetup {
   /**
    * Determines the type of {@link DatabaseOperation operation} that will be used to reset the database.
    * @return The type of operation used to reset the database
    */
   DatabaseOperation type() default DatabaseOperation.CLEAN_INSERT;

   /**
    * Provides the locations of the datasets that will be used to reset the database. Unless otherwise
    * {@link DbUnitConfiguration#dataSetLoader() configured} locations are {@link org.springframework.core.io.ClassRelativeResourceLoader relative}
    * to the class under test.
    * @return The dataset locations
    * @see DbUnitConfiguration#dataSetLoader()
    */
   String[] value();

   String connectionName();
}
