# DBeaver AWS RDS Authentication

DBeaver plugin for connecting to PostgreSQL on Amazon RDS with IAM database
authentication. It provides two authentication models:

- `AWS RDS IAM`: generates an IAM token and connects directly to the RDS endpoint.
- `AWS RDS SSM`: discovers the EC2 and RDS resources by their `Name` tags, opens
  an AWS Systems Manager tunnel, and connects through a user-selected local port.

Both models use the `PostgreSQL AWS RDS IAM` driver and the official PostgreSQL
JDBC driver. No database password is stored.

## Install The Plugin In DBeaver

In DBeaver, open:

```text
Help > Install New Software
```

Use this update site:

```text
https://pauloricardo-ggs.github.io/dbeaver-iam-password-plugin/
```

Select the AWS RDS IAM auth feature, finish the installation, and restart
DBeaver when prompted.

Create a connection using this driver:

```text
PostgreSQL AWS RDS IAM
```

After selecting the driver, choose one of the two supported methods in the
`Authentication` selector:

| Authentication method | Use when |
|---|---|
| `AWS RDS IAM` | The workstation can reach the RDS endpoint directly, such as through a VPN |
| `AWS RDS SSM` | The RDS instance is private and must be reached through an EC2 instance using an SSM tunnel |

Both methods authenticate to the database with an RDS IAM token. The difference
is the network path: `AWS RDS IAM` connects directly, while `AWS RDS SSM`
creates the tunnel automatically before connecting.

The driver handles URLs with this prefix:

```text
jdbc:awsrdsiam:postgresql:
```

## Common Workstation Prerequisites

Both authentication methods require:

- AWS CLI version 2 installed on the workstation.
- An AWS profile configured and authenticated.
- Permission to generate an RDS IAM database authentication token.

Platform-specific installation, authentication, and verification commands are
provided below.

### Install AWS CLI Version 2

The AWS CLI v2 is not installed or updated through `pip`.

#### macOS

Install the official package:

```bash
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "/tmp/AWSCLIV2.pkg"
sudo installer -pkg "/tmp/AWSCLIV2.pkg" -target /
```

Verify the installation and locate the executable:

```bash
/usr/local/bin/aws --version
which -a aws
```

The path normally configured in DBeaver is:

```text
/usr/local/bin/aws
```

#### Linux

Check the processor architecture:

```bash
uname -m
```

For x86_64:

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"
unzip -q "/tmp/awscliv2.zip" -d "/tmp"
sudo /tmp/aws/install
```

For ARM64 (`aarch64`):

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "/tmp/awscliv2.zip"
unzip -q "/tmp/awscliv2.zip" -d "/tmp"
sudo /tmp/aws/install
```

For an existing AWS CLI v2 installation, replace the last command with:

```bash
sudo /tmp/aws/install --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli --update
```

Verify the installation and locate the executable:

```bash
/usr/local/bin/aws --version
which -a aws
```

The path normally configured in DBeaver is:

```text
/usr/local/bin/aws
```

#### Windows

Open Command Prompt or PowerShell as Administrator and run:

```powershell
msiexec.exe /i https://awscli.amazonaws.com/AWSCLIV2.msi
```

Verify the installation and locate the executable:

```powershell
aws --version
where.exe aws
```

The path normally configured in DBeaver is:

```text
C:\Program Files\Amazon\AWSCLIV2\aws.exe
```

Official instructions for all systems:
[Install or update AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).

On every operating system, the version output must start with `aws-cli/2`.
Configure `AWS CLI Path` in DBeaver with the full platform-specific path shown
above so that an older installation is not selected accidentally.

### Verify The AWS Profile

Run the command for the workstation operating system before configuring DBeaver.

#### macOS

```bash
/usr/local/bin/aws sso login --profile development
/usr/local/bin/aws sts get-caller-identity --profile development --region us-east-1
```

The first command is required only for profiles using AWS IAM Identity Center.

#### Linux

```bash
/usr/local/bin/aws sso login --profile development
/usr/local/bin/aws sts get-caller-identity --profile development --region us-east-1
```

The first command is required only for profiles using AWS IAM Identity Center.

#### Windows

```powershell
& "C:\Program Files\Amazon\AWSCLIV2\aws.exe" sso login `
  --profile development
& "C:\Program Files\Amazon\AWSCLIV2\aws.exe" sts get-caller-identity `
  --profile development `
  --region us-east-1
```

The first command is required only for profiles using AWS IAM Identity Center.

## AWS RDS IAM

Use `AWS RDS IAM` when the workstation already has direct network access to the
RDS endpoint, for example through a VPN.

### Prerequisites

- The common AWS CLI and profile prerequisites above are complete.
- The AWS profile can connect as the configured database user.
- Direct network connectivity to the RDS endpoint and port.
- IAM database authentication enabled on the RDS instance.

### Connection URL

Use the real RDS endpoint in the JDBC URL:

```text
jdbc:awsrdsiam:postgresql://{rds-host}:{port}/{database}?sslmode=require
```

Example:

```text
jdbc:awsrdsiam:postgresql://mydb.abc123.us-east-1.rds.amazonaws.com:5432/app?sslmode=require
```

### Authentication Fields

Select `AWS RDS IAM` in the authentication selector and configure:

| Property | Default | Description |
|---|---|---|
| `Username` | — | Database user mapped to IAM authentication in RDS |
| `Session Role` | empty | Optional PostgreSQL role applied with `SET ROLE` after connecting |
| `AWS CLI Path` | `aws` | AWS CLI executable |
| `AWS Profile` | `default` | AWS CLI profile used to generate the token |
| `AWS Region` | `us-east-1` | Region containing the RDS instance |

Before each physical JDBC connection, the plugin runs:

```bash
aws rds generate-db-auth-token
```

It sends the generated token to PostgreSQL as the password and then optionally
executes `SET ROLE`.

Older connections can still pass these settings in the JDBC URL:

| Query parameter | Default |
|---|---|
| `awsRegion` | `us-east-1` |
| `awsProfile` | `default` |
| `awsCliPath` | `aws` |
| `sessionRole` | empty |

## AWS RDS SSM

Use `AWS RDS SSM` when the RDS instance is private and must be reached through
an EC2 instance managed by AWS Systems Manager.

This mode always uses the SSM tunnel. If discovery or tunnel creation fails,
the plugin does not fall back to a direct connection.

### Workstation Prerequisites

- The common AWS CLI and profile prerequisites above are complete.
- AWS Systems Manager Session Manager Plugin installed.
- The selected local port available on `127.0.0.1`.

### Install The Session Manager Plugin

#### macOS

Check the processor architecture first:

```bash
uname -m
```

For Apple Silicon (`arm64`):

```bash
curl \
  "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/mac_arm64/session-manager-plugin.pkg" \
  -o "/tmp/session-manager-plugin.pkg"
```

For Intel (`x86_64`):

```bash
curl \
  "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/mac/session-manager-plugin.pkg" \
  -o "/tmp/session-manager-plugin.pkg"
```

Install the downloaded package and create the standard executable link:

```bash
sudo installer -pkg "/tmp/session-manager-plugin.pkg" -target /
sudo ln -s \
  /usr/local/sessionmanagerplugin/bin/session-manager-plugin \
  /usr/local/bin/session-manager-plugin
```

If the link already exists, the second command can be skipped. Verify:

```bash
/usr/local/bin/session-manager-plugin --version
command -v session-manager-plugin
```

Completely close and reopen DBeaver after installing the executable.

#### Linux

Check the processor architecture:

```bash
uname -m
```

On Debian or Ubuntu x86_64:

```bash
curl \
  "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_64bit/session-manager-plugin.deb" \
  -o "/tmp/session-manager-plugin.deb"
sudo dpkg -i "/tmp/session-manager-plugin.deb"
```

On Debian or Ubuntu ARM64 (`aarch64`):

```bash
curl \
  "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_arm64/session-manager-plugin.deb" \
  -o "/tmp/session-manager-plugin.deb"
sudo dpkg -i "/tmp/session-manager-plugin.deb"
```

Verify the installation:

```bash
session-manager-plugin --version
command -v session-manager-plugin
```

Completely close and reopen DBeaver after installing the executable.

#### Windows

Open PowerShell as Administrator and run:

```powershell
$installer = "$env:TEMP\SessionManagerPluginSetup.exe"
Invoke-WebRequest `
  -Uri "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/windows/SessionManagerPluginSetup.exe" `
  -OutFile $installer
Start-Process $installer -Verb RunAs -Wait
```

Verify the installation:

```powershell
& "C:\Program Files\Amazon\SessionManagerPlugin\bin\session-manager-plugin.exe" --version
where.exe session-manager-plugin
```

If `where.exe` does not find it, add this directory to the Windows `PATH`:

```text
C:\Program Files\Amazon\SessionManagerPlugin\bin\
```

Completely close and reopen DBeaver after installing the executable or changing
the `PATH`.

Official instructions for all systems:
[Install the Session Manager Plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html).

### AWS Infrastructure Prerequisites

- The EC2 instance is `running` and registered as an online Systems Manager managed node.
- The EC2 instance can resolve the private RDS hostname.
- The EC2 Security Group can reach the RDS Security Group on `RDS Port`.
- The RDS instance is `available`, uses PostgreSQL, and has IAM database authentication enabled.
- The EC2 and RDS resources have unique `Name` tag values in the selected region.

Remote-host port forwarding requires SSM Agent `3.1.1374.0` or newer on the EC2
instance.

### Connection URL

The host and port in the JDBC URL are placeholders in SSM mode. The database name
and JDBC query parameters are still used:

```text
jdbc:awsrdsiam:postgresql://ssm:5432/{database}?sslmode=require
```

Example:

```text
jdbc:awsrdsiam:postgresql://ssm:5432/app?sslmode=require
```

The plugin discovers the real RDS endpoint through the `RDS Tag` field.

### Authentication Fields

Select `AWS RDS SSM` in the authentication selector and configure:

| Property | Default | Description |
|---|---|---|
| `EC2 Tag` | — | Value of the EC2 instance's `Name` tag |
| `RDS Tag` | — | Value of the RDS DB instance's `Name` tag |
| `RDS Port` | `5432` | Remote PostgreSQL port reached from the EC2 instance |
| `Local Port` | — | Local port selected by the user for the tunnel |
| `Username` | — | Database user mapped to IAM authentication |
| `Session Role` | empty | Optional PostgreSQL role applied with `SET ROLE` |
| `AWS CLI Path` | `aws` | Full AWS CLI v2 executable path; use the path documented above for the workstation OS |
| `AWS Profile` | `default` | Profile used for discovery, SSM, and the IAM token |
| `AWS Region` | `us-east-1` | Region containing the EC2 and RDS resources |

`EC2 Tag` and `RDS Tag` accept only the tag value. The tag key is always fixed
to `Name`; do not enter `Name=value` in these fields.

Each value must identify exactly one resource:

- One EC2 instance in the `running` state.
- One provisioned PostgreSQL RDS DB instance in the `available` state.

### Connection Flow

When DBeaver opens a connection, the plugin:

1. Resolves the EC2 instance ID from `EC2 Tag` using `Name=<value>`.
2. Resolves the RDS ARN and real endpoint from `RDS Tag` using `Name=<value>`.
3. Starts `AWS-StartPortForwardingSessionToRemoteHost` through the EC2 instance.
4. Waits for `127.0.0.1:<Local Port>` to become available.
5. Generates the IAM token for the real RDS endpoint and `RDS Port`.
6. Connects PostgreSQL through the local port.
7. Optionally executes `SET ROLE`.
8. Keeps the tunnel alive while equivalent JDBC connections are open.
9. Terminates the tunnel after the last connection closes.

### Required AWS Permissions

The selected profile needs permissions equivalent to:

```text
ec2:DescribeInstances
tag:GetResources
rds:DescribeDBInstances
ssm:StartSession
ssm:ResumeSession
ssm:TerminateSession
rds-db:connect
```

Restrict `ssm:StartSession` to the permitted EC2 resources and Session Manager
document whenever possible.

### Current Limitations

- SSM discovery supports provisioned RDS DB instances (`rds:db`).
- Aurora DB clusters (`rds:cluster`) are not resolved.
- The user selects a fixed local port; automatic port allocation is not implemented.
- `sslmode=require` is the supported SSM configuration. `verify-full` needs a
  socket redirection strategy that preserves the real RDS hostname for TLS validation.

### Troubleshooting

`Unknown options: --no-cli-pager`

: The configured executable is AWS CLI v1. Install AWS CLI v2 and set `AWS CLI
  Path` to its full path: `/usr/local/bin/aws` on macOS and Linux, or
  `C:\Program Files\Amazon\AWSCLIV2\aws.exe` on Windows.

`SessionManagerPlugin is not found`

: Follow the platform-specific Session Manager Plugin installation and
  verification instructions above, then completely restart DBeaver. If the
  executable works in a terminal but DBeaver still cannot find it, update this
  plugin to the latest build. The driver adds the configured AWS CLI directory
  and the standard Session Manager Plugin directories to the subprocess `PATH`
  on macOS, Linux, and Windows.

`Local Port ... is already in use`

: Select another local port or stop the process currently listening on that port.

`No ... was found with Name tag`

: Confirm the tag value, AWS profile, region, resource state, and discovery permissions.
