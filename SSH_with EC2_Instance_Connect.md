# How to SSH into EC2 Instances when [EC2 Instance Connect](https://aws.amazon.com/blogs/compute/new-using-amazon-ec2-instance-connect-for-ssh-access-to-your-ec2-instances/#:~:text=Conclusion,(S%C3%A3o%20Paulo)%20AWS%20Regions) is mandated

## Purpose

The following commands demonstrate how to securely connect to an Amazon EC2 instance without requiring direct public SSH access or an open security group. Instead, they use **AWS Systems Manager Session Manager** to create a port-forwarded tunnel, which is then used with standard SSH to log in to the instance. This technique is an alternative to EC2 Instance Connect and offers more security since no incoming SSH ports need to be exposed.

***

## Commands

### 1. Start AWS SSM Port Forwarding Session

```bash
aws ssm start-session \
  --target i-068257852a7215e13 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["22"],"localPortNumber":["2222"]}'
```

- **`--target`**: The instance ID of the EC2 you want to connect to.
- **`--document-name AWS-StartPortForwardingSession`**: Instructs SSM to start a Session Manager port forwarding session.
- **`--parameters`**: Forwards a local port (`2222`) to the EC2’s SSH port (`22`).

After running this command, any connection made to `localhost:2222` will be securely tunneled to port `22` on your EC2 instance.

***

### 2. SSH to EC2 through the Tunnel

```bash
ssh \
  -o "UserKnownHostsFile=/dev/null" \
  -o "StrictHostKeyChecking=no" \
  -i ~/.ssh/ubal2.id_ed25519 \
  -p 2222 \
  ubuntu@localhost
```

- **`-p 2222`**: Connects to the forwarded local port from the SSM session.
- **`-i ~/.ssh/ubal2.id_ed25519`**: Uses your private key for authentication.
- **`ubuntu@localhost`**: Logs in as the `ubuntu` user through the local tunnel.
- **`-o "UserKnownHostsFile=/dev/null"` / `-o "StrictHostKeyChecking=no"`**: Disables host key checks to avoid SSH warnings when tunneling.

***

## SCP (Secure Copy) File Transfer Over the Tunnel

You can also transfer files securely between your local machine and the EC2 instance using `scp` over the established SSM port forwarding tunnel:

```bash
scp -i ~/.ssh/ubal2.id_ed25519 -P 2222 /path/to/local/file ubuntu@localhost:/remote/path/
```

- **`-P 2222`**: Specifies the local forwarded port to connect through the SSM tunnel.
- **`-i ~/.ssh/ubal2.id_ed25519`**: Specifies the private key to authenticate with the EC2 user (`ubuntu`).
- **`/path/to/local/file`**: Path to the file on your local machine you want to copy.
- **`ubuntu@localhost:/remote/path/`**: Target path on the EC2 instance through the local tunnel.

Similarly, to copy files from the EC2 instance to your local machine:

```bash
scp -i ~/.ssh/ubal2.id_ed25519 -P 2222 ubuntu@localhost:/remote/path/file /path/to/local/destination
```


***

## How It Works

1. **Session Manager Tunnel**
The first command uses AWS Systems Manager (SSM) to open a secure port-forwarding session between your local machine and the target EC2 instance, without opening SSH ports to the internet.
2. **SSH Login or SCP File Transfer**
The SSH or SCP client then connects to the local forwarded port, securely tunneling commands or file transfers to the EC2 instance.

***

## Why Not Just Use EC2 Instance Connect?

**EC2 Instance Connect** is another AWS method to establish SSH connections without a pre-shared key, by pushing a temporary SSH key to your instance for a session. It works well for direct SSH, but unlike SSM tunneling:

- EC2 Instance Connect requires port 22 to be accessible from your client (security group rules).
- SSM port forwarding does not require port 22 to be open to the internet at all.

**This makes the SSM approach preferable for private instances or highly restricted environments.**
