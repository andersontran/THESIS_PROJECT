// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.appinventor.rebind;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.rebind.rpc.ServiceInterfaceProxyGenerator;

import java.io.PrintWriter;

/**
 * Service proxy generator that can be used instead of
 * {@link ServiceInterfaceProxyGenerator} to generate client proxies for remote
 * services.
 *
 * <p>The generated proxies implement the
 * {@link com.google.appinventor.client.ExtendedServiceProxy} interface
 * and delegate all additional calls to "normal" proxies generated by
 * {@link ServiceInterfaceProxyGenerator}.
 *
 */
public class ExtendedServiceProxyGenerator extends Generator {
  // Suffix that is appended to the name of the service interface to build the
  // name of the proxy class
  private static final String PROXY_SUFFIX = "_ExtendedProxy";

  // Suffix that is appended to the name of the service interface to build the
  // name of the asynchronous service interface
  private static final String ASYNC_SUFFIX = "Async";

  // Delegate generator to generate "normal" proxies
  private static final Generator PROXY_GENERATOR = new ServiceInterfaceProxyGenerator();

  @Override
  public String generate(TreeLogger logger, GeneratorContext context, String typeName)
      throws UnableToCompleteException {
    // Delegate the creation of the "normal" proxy
    String proxyTypeName = PROXY_GENERATOR.generate(logger, context, typeName);

    // Wrap the generated proxy in an extended proxy
    return generateExtendedProxy(logger, context, typeName, proxyTypeName);
  }

  /**
   * Generates a wrapper around the proxy generated by
   * {@link ServiceInterfaceProxyGenerator}.
   *
   * @param logger log interface
   * @param context generator context
   * @param typeName name of the interface that was passed to
   *        {@link com.google.gwt.core.client.GWT#create(Class)}
   * @param proxyTypeName the name of the wrapped proxy class
   * @return the name of the extended proxy class
   */
  private String generateExtendedProxy(TreeLogger logger, GeneratorContext context,
      String typeName, String proxyTypeName) {
    JClassType type = context.getTypeOracle().findType(typeName);
    String packageName = type.getPackage().getName();
    String className = type.getSimpleSourceName() + PROXY_SUFFIX;
    String asyncName = typeName + ASYNC_SUFFIX;

    String classNameExtendedServiceProxy = "com.google.appinventor.client.ExtendedServiceProxy";

    // The generator can be invoked for the same class name more than once.
    // In this case the GeneratorContext.tryCreate method will return null to
    // indicate that the file already exists. This is not an error.
    PrintWriter out = context.tryCreate(logger, packageName, className);
    if (out != null) {
      out.println("package " + packageName + ";");
      out.println("class " + className);
      out.println("    extends " + classNameExtendedServiceProxy + "<" + typeName + ">");
      out.println("    implements " + ServiceDefTarget.class.getName() + ", " + asyncName + " {");
      out.println("  private " + proxyTypeName + " proxy = new " + proxyTypeName + "();");
      out.println("  public String getServiceEntryPoint() {");
      out.println("    return proxy.getServiceEntryPoint();");
      out.println("  }");
      out.println("  public void setRpcRequestBuilder(" + RpcRequestBuilder.class.getName() +
          " builder) {");
      out.println("    proxy.setRpcRequestBuilder(builder);");
      out.println("  }");
      out.println("  public void setServiceEntryPoint(String address) {");
      out.println("    proxy.setServiceEntryPoint(address);");
      out.println("  }");
      out.println("  public String getSerializationPolicyName() {");
      out.println("    return proxy.getSerializationPolicyName();");
      out.println("  }");

      for (JMethod method : type.getMethods()) {
        printMethod(out, method, typeName);
      }

      out.println("}");

      context.commit(logger, out);
    }

    return packageName + "." + className;
  }

  /**
   * Generate the implementation of a single method.
   *
   * @param out where to print the method to
   * @param method the method
   * @param typeName type name of the containing proxy class
   */
  private void printMethod(PrintWriter out, JMethod method, String typeName) {
    // Build parameter lists
    int i = 0;
    StringBuilder actualParamsBuilder = new StringBuilder();
    StringBuilder formalParamsBuilder = new StringBuilder();
    for (JParameter param : method.getParameters()) {
      if (i != 0) {
        actualParamsBuilder.append(", ");
        formalParamsBuilder.append(", ");
      }

      String paramType = param.getType().getParameterizedQualifiedSourceName();
      String paramName = "p" + i++;
      actualParamsBuilder.append(paramName);
      formalParamsBuilder.append(paramType + " " + paramName);
    }
    String actualParams = actualParamsBuilder.toString();
    String formalParams = formalParamsBuilder.toString();

    // Information about the return type
    JType returnType = method.getReturnType();
    boolean hasReturnValue = !returnType.getSimpleSourceName().equals("void");

    JPrimitiveType primitiveReturnType = returnType.isPrimitive();
    String resultType =
        primitiveReturnType != null ? primitiveReturnType.getQualifiedBoxedSourceName()
            : returnType.getParameterizedQualifiedSourceName();

    String callbackType = AsyncCallback.class.getName() + "<" + resultType + ">";

    // Print method
    out.println("  public void " + method.getName() + "(" + formalParams
        + (formalParams.isEmpty() ? "" : ", ") + "final " + callbackType + " callback" + ") {");
    out.println("    fireStart(\"" + method.getName() + "\"" + (actualParams.isEmpty() ? "" : ", ")
        + actualParams + ");");
    out.println("    proxy." + method.getName() + "(" + actualParams
        + (actualParams.isEmpty() ? "" : ", ") + "new " + callbackType + "() {");
    out.println("      public void onSuccess(" + resultType + " result) {");
    out.println("        " + outcome(method, "Success", "result"));
    out.println("      }");
    out.println("      public void onFailure(Throwable caught) {");
    out.println("        " + outcome(method, "Failure", "caught"));
    out.println("      }");
    out.println("    });");
    out.println("  }");
  }

  /**
   * Generate code to handle the outcome of an RPC.
   *
   * @param method the RPC method that was called
   * @param outcome the outcome: "Success" or "Failure"
   * @param result the result of the RPC call or null for void methods
   * @return the generated code
   */
  private String outcome(JMethod method, String outcome, String result) {
    String callListener = "fire" + outcome + "(\"" + method.getName() + "\", " + result + ");";
    String callCallback = "callback.on" + outcome + "(" + result + ");";

    return callListener + ' ' + callCallback;
  }
}
