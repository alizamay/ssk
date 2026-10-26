package th.co.ais.authenldap;

public class AuthenticateServiceSoapProxy implements th.co.ais.authenldap.AuthenticateServiceSoap {
  private String _endpoint = null;
  private th.co.ais.authenldap.AuthenticateServiceSoap authenticateServiceSoap = null;
  
  public AuthenticateServiceSoapProxy() {
    _initAuthenticateServiceSoapProxy();
  }
  
  public AuthenticateServiceSoapProxy(String endpoint) {
    _endpoint = endpoint;
    _initAuthenticateServiceSoapProxy();
  }
  
  private void _initAuthenticateServiceSoapProxy() {
    try {
      authenticateServiceSoap = (new th.co.ais.authenldap.AuthenticateServiceLocator()).getAuthenticateServiceSoap();
      if (authenticateServiceSoap != null) {
        if (_endpoint != null)
          ((javax.xml.rpc.Stub)authenticateServiceSoap)._setProperty("javax.xml.rpc.service.endpoint.address", _endpoint);
        else
          _endpoint = (String)((javax.xml.rpc.Stub)authenticateServiceSoap)._getProperty("javax.xml.rpc.service.endpoint.address");
      }
      
    }
    catch (javax.xml.rpc.ServiceException serviceException) {}
  }
  
  public String getEndpoint() {
    return _endpoint;
  }
  
  public void setEndpoint(String endpoint) {
    _endpoint = endpoint;
    if (authenticateServiceSoap != null)
      ((javax.xml.rpc.Stub)authenticateServiceSoap)._setProperty("javax.xml.rpc.service.endpoint.address", _endpoint);
    
  }
  
  public th.co.ais.authenldap.AuthenticateServiceSoap getAuthenticateServiceSoap() {
    if (authenticateServiceSoap == null)
      _initAuthenticateServiceSoapProxy();
    return authenticateServiceSoap;
  }
  
  public th.co.ais.authenldap.ServiceResponse authenticate(th.co.ais.authenldap.UserAuthenticate user, th.co.ais.authenldap.ProjectQuery projectCode) throws java.rmi.RemoteException{
    if (authenticateServiceSoap == null)
      _initAuthenticateServiceSoapProxy();
    return authenticateServiceSoap.authenticate(user, projectCode);
  }
  
  
}