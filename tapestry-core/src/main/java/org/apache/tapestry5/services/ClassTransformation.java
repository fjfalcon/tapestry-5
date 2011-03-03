// Copyright 2006, 2007, 2008, 2010 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.services;

import java.lang.annotation.Annotation;
import java.util.List;

import javassist.CtBehavior;

import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.func.Predicate;
import org.apache.tapestry5.internal.transform.ReadOnlyFieldValueConduit;
import org.apache.tapestry5.ioc.AnnotationProvider;
import org.apache.tapestry5.model.MutableComponentModel;
import org.apache.tapestry5.runtime.Component;
import org.apache.tapestry5.runtime.Event;
import org.slf4j.Logger;

/**
 * Contains class-specific information used when transforming a raw component class into an
 * executable component class.
 * An executable class is one that has been transformed to work within Tapestry. This includes
 * adding interfaces
 * ({@link org.apache.tapestry5.runtime.Component}) but also transforming access to fields, based on
 * annotations and
 * naming conventions. Most of the changes are provided by different implementations of
 * {@link ComponentClassTransformWorker}.
 * <p/>
 * Much of this information is somewhat like ordinary reflection, but applies to a class that has not yet been loaded.
 * Field types, return types, parameter types and exception types are represented as string names, since any of them may
 * be a class that has not yet been loaded and transformed as well.
 * <p/>
 * Transformation is primarily about identifying annotations on fields and on methods and changing the class, adding new
 * interfaces, fields and methods, and deleting some existing fields.
 * <p/>
 * A ClassTransformation contains all the state data specific to a particular class being transformed. A number of
 * <em>workers</em> will operate upon the ClassTransformation to effect the desired changes before the true class is
 * loaded into memory.
 * <p/>
 * Instances of this class are not designed to be thread safe, access to an instance should be restricted to a single
 * thread. In fact, the design of this type is to allow stateless singletons in multiple threads to work on
 * thread-specific data (within the ClassTransformation).
 * <p/>
 * The majority of methods concern the <em>declared</em> members (field and methods) of a specific class, rather than
 * any fields or methods inherited from a base class.
 * 
 * @see org.apache.tapestry5.services.TapestryModule#contributeComponentClassTransformWorker(org.apache.tapestry5.ioc.OrderedConfiguration,
 *      org.apache.tapestry5.ioc.ObjectLocator, InjectionProvider, ComponentClassResolver)
 */
public interface ClassTransformation extends AnnotationProvider
{
    /**
     * Returns the fully qualified class name of the class being transformed.
     */
    String getClassName();

    /**
     * Returns the name of a new member (field or method). Ensures that the resulting name does not
     * conflict with any
     * existing member (declared by the underlying class, or inherited from a base class).
     * 
     * @param suggested
     *            the suggested value for the member
     * @return a unique name for the member
     */
    String newMemberName(String suggested);

    /**
     * As with {@link #newMemberName(String)}, but the suggested name is constructed from the prefix
     * and base name. An
     * underscore will separate the prefix from the base name.
     * 
     * @param prefix
     *            for the generated name
     * @param baseName
     *            a name, often of an existing field or method
     * @return a unique name
     */
    String newMemberName(String prefix, String baseName);

    /**
     * Returns a sorted list of declared instance fields with the indicated annotation. Non-private
     * and static fields are ignored.
     * 
     * @since 5.2.0
     */
    List<TransformField> matchFieldsWithAnnotation(Class<? extends Annotation> annotationClass);

    /**
     * Finds all methods matched by the provided predicate.
     * 
     * @param predicate
     *            Used to filter the list
     * @return a list of matching methods (which may be empty) in ascending order (by
     *         method name), but descending order (by parameter count) within overrides of a single method name.
     */
    List<TransformMethod> matchMethods(Predicate<TransformMethod> predicate);

    /**
     * Finds all methods matched by the provided predicate.
     * 
     * @param annotationType
     *            Used to filter the list
     * @return a list of matching methods (which may be empty) in ascending order (by
     *         method name), but descending order (by parameter count) within overrides of a single method name.
     */
    List<TransformMethod> matchMethodsWithAnnotation(Class<? extends Annotation> annotationType);

    /**
     * Finds all unclaimed fields matched by the provided predicate. Only considers instance fields.
     * Added, removed and claimed fields are excluded.
     * 
     * @param predicate
     *            used for matching
     * @return sorted list of matching fields
     * @since 5.2.0
     */
    List<TransformField> matchFields(Predicate<TransformField> predicate);

    /**
     * Locates a declared field by its field name. The field must exist.
     * 
     * @param name
     *            of declared field
     * @return field information
     * @throws RuntimeException
     *             if no such field
     * @since 5.2.0
     */
    TransformField getField(String fieldName);

    /**
     * Finds any declared <em>instance</em> fields that have not been claimed (via {@link #claimField(String, Object)})
     * and have not been added , and returns the names of those fields. May return an empty array.
     * 
     * @deprecated Use {@link #matchUnclaimedFields()} instead
     */
    List<String> findUnclaimedFields();

    /**
     * Matches all fields that are not claimed. This may include static fields and final fields, but will not
     * include fields that have been added as part of the transformation.
     * 
     * @since 5.2.0
     * @return sorted list of unclaimed fields
     */
    List<TransformField> matchUnclaimedFields();

    /**
     * Returns true if the indicated name is a private instance field.
     * 
     * @param fieldName
     * @return true if field exists
     */
    boolean isField(String fieldName);

    /**
     * Defines a new declared field for the class. Suggested name may be modified to ensure uniqueness.
     * 
     * @param modifiers
     *            modifiers for the field (typically, {@link java.lang.reflect.Modifier#PRIVATE})
     * @param type
     *            the type for the field, as a string
     * @param suggestedName
     *            the desired name for the field, which may be modified (for uniqueness) when
     *            returned
     * @return new field instance
     */
    TransformField createField(int modifiers, String type, String suggestedName);

    /**
     * Defines a new <strong>protected</strong> instance variable whose initial value is provided
     * statically, via a
     * constructor parameter. The transformation caches the result, so calling this method
     * repeatedly with the same type
     * and value will return the same field name. Caching extends to the parent transformation, so
     * that a value injected
     * into a parent class will be available (via the protected instance variable) to subclasses.
     * This is primarily used to inject service dependencies into components, though it has a number
     * of other uses as well.
     * 
     * @param type
     *            the type of object to inject
     * @param suggestedName
     *            the suggested name for the new field
     * @param value
     *            to be injected. This value is retained.
     * @return the actual name of the injected field
     */
    String addInjectedField(Class type, String suggestedName, Object value);

    /**
     * Like {@link #addInjectedField(Class, String, Object)}, but instead of specifying the value,
     * a provider for the value is specified. In the generated class' constructor, the provider
     * will be passed the {@link ComponentResources} and will return the final value; thus
     * each component <em>instance</em> will receive a matching unique instance via the provider.
     * 
     * @param <T>
     * @param type
     *            type of value to inject
     * @param suggestedName
     *            suggested name for the new field
     * @param provider
     *            injected into the component to provide the value
     * @return the actual name of the injected field
     * @since 5.2.0
     */
    <T> TransformField addIndirectInjectedField(Class<T> type, String suggestedName, ComponentValueProvider<T> provider);

    /**
     * Transforms the class to implement the indicated interface. If the class (or its super class)
     * does not already
     * implement the interface, then the interface is added, and default implementations of any
     * methods of the interface
     * are added.
     * <p/>
     * TODO: Checking that the names of methods in the interface do not conflict with the names of methods present in
     * the (unmodified) class.
     * 
     * @param interfaceClass
     *            the interface to be implemented by the class
     * @throws IllegalArgumentException
     *             if the interfaceClass argument does not represent an interface
     */
    void addImplementedInterface(Class interfaceClass);

    /**
     * Returns the name of a field that provides the {@link org.apache.tapestry5.ComponentResources} for the transformed
     * component. This will be a protected field, accessible to the class and subclasses.
     * 
     * @return name of field
     * @deprecated Obtain the resources from {@link ComponentMethodInvocation#getComponentResources()} or
     *             as passed to {@link ComponentValueProvider#get(ComponentResources)} instead
     */
    String getResourcesFieldName();

    /**
     * Replaces all read-references to the specified field with invocations of the specified method
     * name. Replacements
     * do not occur in methods added via {@link #addMethod(TransformMethodSignature, String)} or
     * {@link #extendMethod(TransformMethodSignature, String)}.
     * 
     * @deprecated Use {@link TransformField#replaceAccess(ComponentValueProvider) instead
     */
    void replaceReadAccess(String fieldName, String methodName);

    /**
     * Replaces all write accesses to the specified field with invocations of the specified method
     * name. The method
     * should take a single parameter of the same type as the field. Replacements do not occur in
     * methods added via {@link #addMethod(TransformMethodSignature, String)} or
     * {@link #extendMethod(TransformMethodSignature, String)}.
     * 
     * @deprecated Use {@link TransformField#replaceAccess(ComponentValueProvider) instead
     */
    void replaceWriteAccess(String fieldName, String methodName);

    /**
     * Converts a type name into a corresponding class (possibly, a transformed class). Primitive
     * type names are returned as wrapper types.
     */
    Class toClass(String type);

    /**
     * Returns a logger, based on the class name being transformed, to which warnings or errors
     * concerning the class being transformed may be logged.
     */
    Logger getLogger();

    /**
     * Returns the modifiers for the named field.
     * 
     * @deprecated Use {@link TransformField#getModifiers()} instead
     */
    int getFieldModifiers(String fieldName);

    /**
     * Converts a signature to a string used to identify the method; this consists of the
     * {@link TransformMethodSignature#getMediumDescription()} appended with source file information
     * and line number
     * information (when available).
     * 
     * @param signature
     * @return a string that identifies the class, method name, types of parameters, source file and
     *         source line number
     * @deprecated Use {@link TransformMethod#getMethodIdentifier()} instead
     */
    String getMethodIdentifier(TransformMethodSignature signature);

    /**
     * Returns true if this transformation represents a root class (one that extends directly from
     * Object), or false if this transformation is an sub-class of another transformed class.
     * 
     * @return true if root class, false if sub-class
     */
    boolean isRootTransformation();

    /**
     * Adds method advice for the indicated method.
     * 
     * @deprecated Use {@link TransformMethod#addAdvice(ComponentMethodAdvice)} instead
     */
    void advise(TransformMethodSignature methodSignature, ComponentMethodAdvice advice);

    /**
     * Returns true if the method is an override of a method from the parent class.
     * 
     * @param methodSignature
     *            signature of method to check
     * @return true if the parent class contains a method with the name signature
     * @deprecated Use {@link TransformMethod#isOverride()} instead
     */
    boolean isMethodOverride(TransformMethodSignature methodSignature);

    /**
     * Locates and returns the method if declared in this class; If not,
     * the method is added to the class. If the method is an override
     * of a base class method, then the method will delegate to the base
     * class method (invoke it, return its value). If the method is entirely
     * new, it will ignore its parameters and return a default value (null, 0 or false).
     * 
     * @param signature
     *            identifies the method to locate, override or create
     * @since 5.2.0
     */
    TransformMethod getOrCreateMethod(TransformMethodSignature signature);

    /**
     * Determines if the class being transformed includes a declared (not inherited) method
     * with the provided signature.
     * 
     * @since 5.2.0
     * @param signature
     *            identifies method to search for
     * @return true if a such a method exists
     */
    boolean isDeclaredMethod(TransformMethodSignature signature);

    /**
     * Adds advice to the {@link Component#dispatchComponentEvent(org.apache.tapestry5.runtime.ComponentEvent)} method.
     * If the handler is invoked,
     * the return value of the method will be overriden to true. Updates
     * {@linkplain MutableComponentModel#addEventHandler(String) the model} to
     * indicate that there is a handler for the named event. Existing handlers, or super-class handlers,
     * are invoked <em>first</em>.
     * 
     * @param eventType
     *            name of event to be handled
     * @param minContextValues
     *            minimum number of event context values required to invoke the method
     * @param methodDescription
     *            Text description of what the handler does (used with {@link Event#setMethodDescription(String)})
     * @param handler
     *            the handler to invoke
     * @since 5.2.0
     */
    void addComponentEventHandler(String eventType, int minContextValues, String methodDescription,
            ComponentEventHandler handler);
}
