import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.builder.BlockDeviceMappingBuilder;
import org.openstack4j.model.image.v2.ContainerFormat;
import org.openstack4j.model.image.v2.DiskFormat;
import org.openstack4j.model.image.v2.Image;
import org.openstack4j.model.network.*;
import org.openstack4j.model.network.options.PortListOptions;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.openstack.OSFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class Main {
    public static void main(String[] args) throws MalformedURLException {
        // use Identifier.byId("domainId") or Identifier.byName("example-domain")
        Identifier domainIdentifier = Identifier.byName("Default");

        // unscoped authentication
        // as the username is not unique across domains you need to provide the domainIdentifier
        /*OSClient.OSClientV3 osClientV3 = OSFactory.builderV3()
                .endpoint("http://192.168.1.200:35357/v3")
                .credentials("admin","test123", domainIdentifier)
                .authenticate();
        */
        // project scoped authentication

        OSClient.OSClientV3 osClientV3 = OSFactory.builderV3()
                .endpoint("http://192.168.1.200:5000/v3")
                .credentials("admin", "test123", domainIdentifier)
                .scopeToProject(Identifier.byName("admin"), Identifier.byName("Default"))
                .authenticate();

        Volume volume1 = osClientV3.blockStorage().volumes()
                        .create(Builders.volume()
                        .name("First Volume")
                        .description("Simple volume to store backups on")
                        .size(20)
                        .bootable(true)
                        .build()
                );

        Image image1 = osClientV3.imagesV2().create(
                Builders.imageV2()
                        .name("Ubuntu")
                        .osVersion("ubuntu")
                        .containerFormat(ContainerFormat.BARE)
                        .visibility(Image.ImageVisibility.PUBLIC)
                        .diskFormat(DiskFormat.QCOW2)
                        .minDisk(0L)
                        .minRam(0L)
                        .build()
        );

        Payload<URL> payload = Payloads.create(new URL("https://cloud-images.ubuntu.com/bionic/current/bionic-server-cloudimg-amd64.img"));

        // Get image object to use, or send null
        Image image_up = osClientV3.imagesV2().get("imageId");

        ActionResponse upload = osClientV3.imagesV2().upload(
                image1.getId(),
                payload,
                image1);

        Network exnetwork = osClientV3.networking().network()
                .create(Builders.network()
                        .adminStateUp(true)
                        .physicalNetwork("providerPhysicalNetwork")
                        .networkType(NetworkType.FLAT)
                        .isRouterExternal(true)
                        .name("ext_net")
                        .build());

        Subnet ext_sub = osClientV3.networking().subnet().create(Builders.subnet()
                .name("ext_sub")
                .networkId(exnetwork.getId())
                .addPool("211.183.3.100", "211.183.3.200")
                .ipVersion(IPVersionType.V4)
                .gateway("211.183.3.2")
                .addDNSNameServer("8.8.8.8")
                .cidr("211.183.3.0/24")
                .build());

        Network innetwork = osClientV3.networking().network()
                .create(Builders.network()
                        .adminStateUp(true)
                        .networkType(NetworkType.VXLAN)
                        .name("int_net")
                        .build());

        Subnet int_sub = osClientV3.networking().subnet().create(Builders.subnet()
                .name("int_sub")
                .networkId(innetwork.getId())
                .addPool("192.168.100.101", "192.168.100.200")
                .ipVersion(IPVersionType.V4)
                .gateway("192.168.100.1")
                .cidr("192.168.100.0/24")
                .build());

        Router router = osClientV3.networking().router().create(Builders.router()
                .name("router1")
                .adminStateUp(true)
                .externalGateway(exnetwork.getId(),true)
                .build());

        //Remove to Check if it is possible to create servers without specified port for internal network.
        Port int_port1 = osClientV3.networking().port().create(Builders.port()
                .name("int_port1")
                .networkId(innetwork.getId())
                .fixedIp("192.168.100.150", int_sub.getId())
                .build());

        Keypair kp = osClientV3.compute().keypairs().create("First-keypair", null);

        SecGroupExtension scg = osClientV3.compute().securityGroups().create("First Group", "Permits ICMP and SSH");
        // Permit Port 80 against an existing Group for anyone
        SecGroupExtension.Rule rule = osClientV3.compute().securityGroups()
                        .createRule(Builders.secGroupRule()
                        .parentGroupId(scg.getId())
                        .protocol(IPProtocol.TCP)
                        .cidr("0.0.0.0/0")
                        .range(80, 80).build());

        BlockDeviceMappingBuilder blockDeviceMappingBuilder = Builders.blockDeviceMapping()
                .uuid(volume1.getId())
                .deviceName("/dev/vdb")
                .bootIndex(0);

        ServerCreate sc = Builders.server()
                        .name("instance1")
                        .flavor("2")
                        .image(image1.getId())
                        .addNetworkPort(int_port1.getId())
                        .blockDevice(blockDeviceMappingBuilder.build())
                        .addSecurityGroup("First Group").keypairName("First-keypair").build();

        Server server = osClientV3.compute().servers().boot(sc);

        // Identify server port based on the server ID and the private network
        Port port = osClientV3.networking().port().list(
                PortListOptions.create().deviceId(server.getId()).networkId(innetwork.getId())).get(1);

        // Create floating IP in the public network
        NetFloatingIP fip = Builders.netFloatingIP().portId(port.getId()).floatingNetworkId(exnetwork.getId()).build();
        fip = osClientV3.networking().floatingip().create(fip);
    }
}


