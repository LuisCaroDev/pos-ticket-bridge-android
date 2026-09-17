param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(8)

function Assert-Endpoint($Method, $Path, $ExpectedStatus) {
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method), "$($BaseUrl.TrimEnd('/'))$Path")
    # Each request must exercise a fresh Netty pipeline, not a reused connection.
    $request.Headers.ConnectionClose = $true
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ([int]$response.StatusCode -ne $ExpectedStatus) {
                throw "$Method $Path returned $([int]$response.StatusCode): $body"
            }
            if ($Path -eq '/health' -and -not ($body | ConvertFrom-Json).ok) {
                throw "Invalid health response: $body"
            }
            if ($ExpectedStatus -eq 401 -and ($body | ConvertFrom-Json).error.code -ne 'invalid_token') {
                throw "Invalid authentication response: $body"
            }
            Write-Output "$Method $Path -> $ExpectedStatus"
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}

try {
    # The release regression can leave the listener bound while dropping requests.
    1..5 | ForEach-Object { Assert-Endpoint 'GET' '/health' 200 }
    # No token: validate routing/authentication without printing or opening a drawer.
    foreach ($path in @('/print', '/open-drawer', '/test/release-smoke')) {
        Assert-Endpoint 'POST' $path 401
    }
    Assert-Endpoint 'OPTIONS' '/print' 204
} finally { $client.Dispose() }
