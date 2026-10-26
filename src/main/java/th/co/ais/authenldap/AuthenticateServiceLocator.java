/**
 * AuthenticateServiceLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package th.co.ais.authenldap;

    public class AuthenticateServiceLocator extends org.apache.axis.client.Service implements th.co.ais.authenldap.AuthenticateService {

    public AuthenticateServiceLocator() {
    }


    public AuthenticateServiceLocator(org.apache.axis.EngineConfiguration config) {
        super(config);
    }

    public AuthenticateServiceLocator(java.lang.String wsdlLoc, javax.xml.namespace.QName sName) throws javax.xml.rpc.ServiceException {
        super(wsdlLoc, sName);
    }

    // Use to get a proxy class for AuthenticateServiceSoap
    private java.lang.String AuthenticateServiceSoap_address = "http://dev-ldapservicews.ais.co.th/Authenticate/authenticateservice.asmx";

    public java.lang.String getAuthenticateServiceSoapAddress() {
        return AuthenticateServiceSoap_address;
    }

    // The WSDD service name defaults to the port name.
    private java.lang.String AuthenticateServiceSoapWSDDServiceName = "AuthenticateServiceSoap";

    public java.lang.String getAuthenticateServiceSoapWSDDServiceName() {
        return AuthenticateServiceSoapWSDDServiceName;
    }

    public void setAuthenticateServiceSoapWSDDServiceName(java.lang.String name) {
        AuthenticateServiceSoapWSDDServiceName = name;
    }

    public th.co.ais.authenldap.AuthenticateServiceSoap getAuthenticateServiceSoap() throws javax.xml.rpc.ServiceException {
       java.net.URL endpoint;
        try {
            endpoint = new java.net.URL(AuthenticateServiceSoap_address);
        }
        catch (java.net.MalformedURLException e) {
            throw new javax.xml.rpc.ServiceException(e);
        }
        return getAuthenticateServiceSoap(endpoint);
    }

    public th.co.ais.authenldap.AuthenticateServiceSoap getAuthenticateServiceSoap(java.net.URL portAddress) throws javax.xml.rpc.ServiceException {
        try {
            th.co.ais.authenldap.AuthenticateServiceSoapStub _stub = new th.co.ais.authenldap.AuthenticateServiceSoapStub(portAddress, this);
            _stub.setPortName(getAuthenticateServiceSoapWSDDServiceName());
            return _stub;
        }
        catch (org.apache.axis.AxisFault e) {
            return null;
        }
    }

    public void setAuthenticateServiceSoapEndpointAddress(java.lang.String address) {
        AuthenticateServiceSoap_address = address;
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        try {
            if (th.co.ais.authenldap.AuthenticateServiceSoap.class.isAssignableFrom(serviceEndpointInterface)) {
                th.co.ais.authenldap.AuthenticateServiceSoapStub _stub = new th.co.ais.authenldap.AuthenticateServiceSoapStub(new java.net.URL(AuthenticateServiceSoap_address), this);
                _stub.setPortName(getAuthenticateServiceSoapWSDDServiceName());
                return _stub;
            }
        }
        catch (java.lang.Throwable t) {
            throw new javax.xml.rpc.ServiceException(t);
        }
        throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " + (serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(javax.xml.namespace.QName portName, Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        if (portName == null) {
            return getPort(serviceEndpointInterface);
        }
        java.lang.String inputPortName = portName.getLocalPart();
        if ("AuthenticateServiceSoap".equals(inputPortName)) {
            return getAuthenticateServiceSoap();
        }
        else  {
            java.rmi.Remote _stub = getPort(serviceEndpointInterface);
            ((org.apache.axis.client.Stub) _stub).setPortName(portName);
            return _stub;
        }
    }

    public javax.xml.namespace.QName getServiceName() {
        return new javax.xml.namespace.QName("http://authenldap.ais.co.th/", "AuthenticateService");
    }

    private java.util.HashSet ports = null;

    public java.util.Iterator getPorts() {
        if (ports == null) {
            ports = new java.util.HashSet();
            ports.add(new javax.xml.namespace.QName("http://authenldap.ais.co.th/", "AuthenticateServiceSoap"));
        }
        return ports.iterator();
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(java.lang.String portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        
if ("AuthenticateServiceSoap".equals(portName)) {
            setAuthenticateServiceSoapEndpointAddress(address);
        }
        else 
{ // Unknown Port Name
            throw new javax.xml.rpc.ServiceException(" Cannot set Endpoint Address for Unknown Port" + portName);
        }
    }

    /**
    * Set the endpoint address for the specified port name.
    */
    public void setEndpointAddress(javax.xml.namespace.QName portName, java.lang.String address) throws javax.xml.rpc.ServiceException {
        setEndpointAddress(portName.getLocalPart(), address);
    }

}
