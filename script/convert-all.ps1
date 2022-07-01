#!/powershell
$jar = "../build/libs/german-fhir-terminology-0.0.1-SNAPSHOT.jar"
$workdir = (Get-Location).Path
Write-Output "Running from: $workdir"
$output = "$workdir/output"
mkdir -Force $output | Out-Null
$logdir = "$workdir/logs"
mkdir -Force $logdir | Out-Null


$basepath = "Y:\Terminology-Resources" # TODO CHANGE ME!
$dir = "ICD10GM" # TODO CHANGE ME TOO!


$files = Get-ChildItem "$basepath/$dir" | sort
$pattern = "(icd10gm|ops)(\d{2,4}).*.zip"
foreach ($f in $files) {
    $matches = [regex]::Match($f.Name, $pattern)
    if (!$matches.Success) {
        Write-Error "Filename $f does not match regex $pattern"
        continue
    }
    if ($f.Name.EndsWith("zip.bak")) {
        Write-Output "File $f is a zip.bak file, skipping"
        continue
    }
    $version = $matches.Groups[2].Value
    if ($version.Length -eq 2) {
        $version = $version[0] + "." + $version[1]
    }
    $fullname = $f.FullName
    Write-Output "$fullname - Version $version"
    java -jar $jar -o $output -r SGML -t $dir -i $fullname -v $version | Tee-Object "$logdir/$dir-$version.log"
}
