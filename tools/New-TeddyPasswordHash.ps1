[CmdletBinding()]
param(
    [System.Security.SecureString] $Password,
    [ValidateRange(10000, 1000000)]
    [int] $Iterations = 120000
)

$ErrorActionPreference = 'Stop'

if ($null -eq $Password) {
    $Password = Read-Host 'Teddy admin password' -AsSecureString
}

$passwordPointer = [IntPtr]::Zero
$plainPassword = $null
$random = $null
$derive = $null
try {
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if ([string]::IsNullOrEmpty($plainPassword)) {
        throw 'Password must not be empty.'
    }

    $salt = New-Object byte[] 16
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($salt)

    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $plainPassword,
        $salt,
        $Iterations,
        [Security.Cryptography.HashAlgorithmName]::SHA256)
    $hash = $derive.GetBytes(32)

    'pbkdf2-sha256${0}${1}${2}' -f (
        $Iterations,
        [Convert]::ToBase64String($salt),
        [Convert]::ToBase64String($hash))
} finally {
    if ($null -ne $derive) { $derive.Dispose() }
    if ($null -ne $random) { $random.Dispose() }
    $plainPassword = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
