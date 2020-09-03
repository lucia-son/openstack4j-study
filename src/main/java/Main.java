import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Domain;
import org.openstack4j.model.identity.v3.User;
import org.openstack4j.openstack.OSFactory;

import java.util.List;


public class Main {
    public static void main(String[] args) {
        // use Identifier.byId("domainId") or Identifier.byName("example-domain")
        Identifier domainIdentifier = Identifier.byId("default");

        // unscoped authentication
        // as the username is not unique across domains you need to provide the domainIdentifier
        OSClient.OSClientV3 osClientV3 = OSFactory.builderV3()
                .endpoint("http://192.168.1.200:35357/v3")
                .
                .credentials("admin","test123",domainIdentifier)
                .authenticate();

        List<? extends Domain> domainList = osClientV3.identity().domains().list();

        List<? extends User> users = osClientV3.identity().users().list();

        System.out.println("Initialize the index value for for statement");
        System.out.println("--------------------------------------------");
        for(int i = 0; i<users.size();i++){
            System.out.println(users.get(i).getId());
        }
        System.out.println("--------------------------------------------");

        System.out.println("Just use the Enhanced for loop");
        System.out.println("--------------------------------------------");
        for(User user:users){
            System.out.println(user.getId());
        }
        System.out.println("--------------------------------------------");
    }

}


