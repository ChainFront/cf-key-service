*Bringing security and usability to enterprise blockchain applications*

# Introduction

The ChainFront Cloud Services API enables developers to quickly and easily integrate their solutions with blockchain networks.
The API endpoints support connecting to the Stellar, Ripple (XRP), Ethereum, and Bitcoin networks. 
By using ChainFront, you provide your users with a secure and easy way to approve transactions without the need for
them to manage their own private keys. The private keys are created within the secure environment provided by ChainFront 
and never leave this environment, nor are they ever viewable. You can easily create accounts, transfer tokens, and invoke 
contracts, all with the benefit of requiring multi-factor authentication for any transaction signer.

The current version of the API is **v1**. 

# Getting Started

The ChainFront Cloud Service API is a RESTful API. It allows you to create accounts, configure multi-factor transaction
approval methods, create blockchain addresses, create and sign transactions, and submit transactions to a blockchain network.

All production API requests are made to:

`https://api.chainfront.io/api/v1`

There is also a testing sandbox to use when developing and testing applications, with requests being made to:

`https://sandbox.chainfront.io/api/v1`

All requests to the ChainFront REST API are served over HTTPS. Unencrypted HTTP is not supported.

The API is available to ChainFront customers and integration partners and all organizations must acquire valid credentials prior to using the web services.
All API endpoints require authentication. Please see the *Security* section below for additional information.

# Security

### Production Environments
For production usage, the ChainFront REST API uses the OAuth 2.0 protocol to authorize calls. 

When you integrate with ChainFront, you will receive a set of OAuth client ID and secret credentials for your app for 
both the sandbox and production environments. You pass these credentials via the Authorization header 
of the get_access_token request.

In exchange for these credentials, the ChainFront authorization server issues a bearer access token that you use 
for authorization when you make REST API requests. A bearer token enables you to complete actions on behalf and 
with the approval of the resource owner.

### Sandbox Environments
In the sandbox environment, for ease of testing you may instead opt to use an API key via HTTP Basic Authentication. 

To authenticate using an API key, use HTTP basic auth with the username set to the API key's name and the password set to the API key's secret.

Most client software provides a mechanism for supplying a user name and password and will build the required authentication header automatically.

In the case where you need to manually construct and send a basic auth header with your API key, perform the following steps:
1. Build a string of the form apiKeyName:apiKeySecret
2. Base64 encode the string
3. Supply an "Authorization" header with content "Basic " followed by the encoded string. For example, the string "apiKeyName:apiKeySecret" encodes to "YXBpS2V5TmFtZTphcGlLZXlTZWNyZXQ=" in base64,
so you would add the following HTTP header to your request:
`Authorization: Basic YXBpS2V5TmFtZTphcGlLZXlTZWNyZXQ=`

In addition, you will need to pass a `X-CUSTOMER-ID` header with all requests. The value of this header will be provided
to you when you sign up. 


## Authorization

<!-- ReDoc-Inject: <security-definitions> -->


## Multi-Factor Transaction Approvals

In a traditional blockchain security architecture, users are responsible for keeping a private key secure. In a traditional
enterprise application, users are accustomed to password recovery, rolling passwords, and generally are not expected to 
manage their own password backups. ChainFront blends these approaches by abstracting the private key away from the user,
in exchange for the user presenting a multi-factor approval via a device they have proved they own.

Each account can be configured with a transaction approval method. For every transaction in which the account is a signer, the approval 
method indicates how the user is to approve the transaction. 

* **AUTHY_PUSH**: sends a push approval request to the registered Authy id. 
* **CHAINFRONT_TOTP**: using ChainFront UIs, the user will have to scan a QR Code with Google Authenticator or Authy, and will be prompted to enter their TOTP code. 
* **APP_TOTP**: Your app will need to collect a TOTP code from the user and pass it in the X-TOTP-Code header. 
* **IMPLICIT**: No approval will be required for transactions with this account. Use this flow when your application handles all security related to account identity.
                     
| Tx Approval Method | Requires User Approval | Approval Type     | Registration Flow           | Who Collects Approval | Supports Additional Approvers |
| ------------------ |:-----------------------| :---------------  | :-------------------------  | :-------------------- | :---------------------------  |
| AUTHY_PUSH         | yes                    | Push Notification | SMS prompt to install Authy | ChainFront            | yes                           |
| CHAINFRONT_TOTP    | yes                    | TOTP Code         | SMS code                    | ChainFront            | yes                           |
| APP_TOTP           | yes                    | TOTP Code         | SMS code                    | Your App              | no                            |
| IMPLICIT           | no                     | None              | None                        | None                  | no                            |   

# Supported Data Formats
The ChainFront APIs use JSON as the default format. 

Ensure you set the following HTTP headers in order for JSON to be properly processed:

`Content-Type: application/json`

`Accept: application/json`


# Software Development Kits (SDKs)
While you can use ChainFront APIs by making direct HTTP requests, we provide client library code for all our APIs that 
make it easier to access them from your favorite languages. 

Currently we offer Java, Javascript, Go, and Python client libraries. SDKs for other languages can be generated by using OpenAPI tooling.

To obtain the latest SDKs please contact us at [info@chainfront.io](mailto:info@chainfront.io). 



# Examples
In this example we show how to onboard a user, configure multi-factor transaction approval, and submit a transaction to the Stellar blockchain.
Note that in this example we are using Authy push approvals as our MFA method. Also for simplicity we're using API keys rather
than OAuth 2 flows.

### Step 1: Create User Account

```bash
curl -X POST \
  https://sandbox.chainfront.io/api/v1/accounts \
  -H 'accept: application/json' \
  -H 'authorization: Basic Y2ZhZG1pbkB......' \  
  -H 'content-type: application/json' \  
  -H 'x-customer-id: MY_CUSTOMER_ID' \
  -d '{
	"userName": "user01",
	"email": "user@mycompany.com",
	"phone": "555-555-5555",
	"txApprovalMethod": "AUTHY_PUSH"
}'
``` 

This will create an account for user 'user01' in a state of 'PENDING', and send an SMS to the user instructing them
how to enable Authy on their phone. Once the user has enabled Authy, the account will move to a state of 'ACTIVE'.

Take note of the 'cfAccountid' value returned in the response body, as this will be used in downstream calls to identify
the user. We're assuming that the returned id is '1000' for the remaining steps.

### Step 2: Create a Stellar Address

```bash
curl -X POST \
  https://sandbox.chainfront.io/api/v1/accounts/1000/stellar \
  -H 'accept: application/json' \
  -H 'authorization: Basic Y2ZhZG1pbkB.......' \  
  -H 'content-type: application/json' \  
  -H 'x-customer-id: MY_CUSTOMER_ID' \
  -d '{
}'
```

This call will create a Stellar address for the user. Since we're in a test environment, the account will also be funded with 
some fake XLMs. 


### Step 3: Create a Payment Transaction

Now you'd like to send XLM to another ChainFront user.

First, generate a unique "idempotency key" to prevent this payment from being applied more than once.

```bash
export IDEMPOTENCY_KEY=`uuidgen -r`
```

Now you can create a payment transaction using that key:

```bash
curl -X POST \
  https://sandbox.chainfront.io/api/v1/stellar/transactions/payments \
  -H 'accept: application/json' \
  -H 'authorization: Basic Y2ZhZG1pbkB.......' \  
  -H 'content-type: application/json' \  
  -H 'x-customer-id: MY_CUSTOMER_ID' \  
  -H 'x-idempotency-key: $IDEMPOTENCY_KEY' \
  -d '{
	"sourceCfAccountIdentifier": { "type" : "ID", "identifier":"1000" },
	"destinationCfAccountIdentifier": { "type" : "ID", "identifier":"1001" },
	"amount": "50",
	"assetCode": "native",
	"memo": "This is a test memo"
}'
```

This will request approval from user id 1000, and once granted, will create, sign, and submit a transaction to Stellar
to send 50 XLM to user 1001.

### Step 4: Check Account Balances

To verify that our payment transaction was successfully applied to the ledger, we'll invoke the ChainFront APIs
to check the Stellar account balances.

```bash
curl -X GET \
  https://sandbox.chainfront.io/api/v1/accounts/1000/stellar \
  -H 'accept: application/json' \
  -H 'authorization: Basic Y2ZhZG1pbkB.......' \  
  -H 'content-type: application/json' \  
  -H 'x-customer-id: MY_CUSTOMER_ID' 
```

Response:

```json
{
    "cfAccountId": 1000,
    "address": "GCFZ5N7NRKPQK5ZCK3CVUFA4ATXXE6HIPXBXOIMDAGXKFDTEI26GM5R3",
    "balances": [
        {
            "assetType": "native",
            "assetCode": "XLM",
            "amount": "9949.99995"
        }
    ],
    "blacklistAddresses": [],
    "whitelistAddresses": [],
    "createdDate": "2018-10-23T03:35:05.479158Z",
    "lastModifiedDate": "2018-10-23T03:35:05.479158Z"
}
```

For account 1001:

```bash
curl -X GET \
  https://sandbox.chainfront.io/api/v1/accounts/1001/stellar \
  -H 'accept: application/json' \
  -H 'authorization: Basic Y2ZhZG1pbkB.......' \  
  -H 'content-type: application/json' \  
  -H 'x-customer-id: MY_CUSTOMER_ID' 
```

Response:
```json
{
    "cfAccountId": 1001,
    "address": "GBH4TZYZ4IRCPO44CBOLFUHULU2WGALXTAVESQA6432MBJMABBB4GIYI",
    "balances": [
        {
            "assetType": "native",
            "assetCode": "XLM",
            "amount": "10050.0"
        }
    ],
    "blacklistAddresses": [],
    "whitelistAddresses": [],
    "createdDate": "2018-10-23T03:35:05.479158Z",
    "lastModifiedDate": "2018-10-23T03:35:05.479158Z"
}
```

We see that the payment of 50 XLM was successful, and the required Stellar transaction fees were also deducted from the source account.