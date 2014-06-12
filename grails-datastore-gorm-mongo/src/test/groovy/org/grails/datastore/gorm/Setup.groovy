package org.grails.datastore.gorm

import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.grails.datastore.gorm.mongo.MongoGormEnhancer
import org.springframework.context.support.GenericApplicationContext
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.transactions.DatastoreTransactionManager
import org.springframework.util.StringUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator

/**
 * @author graemerocher
 */
class Setup {

    static mongo
    static session

    static destroy() {
        session.nativeInterface.dropDatabase()
    }

    static Session setup(classes) {
        mongo = new MongoDatastore()
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        mongo.applicationContext = ctx
        mongo.afterPropertiesSet()

        for (cls in classes) {
            mongo.mappingContext.addPersistentEntity(cls)
        }

        PersistentEntity entity = mongo.mappingContext.persistentEntities.find { PersistentEntity e -> e.name.contains("TestEntity")}

        mongo.mappingContext.addEntityValidator(entity, [
            supports: { Class c -> true },
            validate: { Object o, Errors errors ->
                if (!StringUtils.hasText(o.name)) {
                    errors.rejectValue("name", "name.is.blank")
                }
            }
        ] as Validator)

        def enhancer = new MongoGormEnhancer(mongo, new DatastoreTransactionManager(datastore: mongo))
        enhancer.enhance()

        mongo.mappingContext.addMappingContextListener({ e ->
            enhancer.enhance e
        } as MappingContext.Listener)

        mongo.applicationContext.addApplicationListener new DomainEventListener(mongo)
        mongo.applicationContext.addApplicationListener new AutoTimestampEventListener(mongo)

        session = mongo.connect()

        return session
    }
}