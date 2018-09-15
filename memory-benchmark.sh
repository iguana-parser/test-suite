
function parse()
{
	fileName="$1"
	heapsize="$2"

    result=`java -Xss4m -XX:+UseG1GC "-Xmx${heapsize}m" -cp target/benchmarks.jar iguana.SingleFileIguanaRun "$fileName" 2>/dev/null`

	if [ $? -eq 0 ]; then
		echo "${result}${heapsize}"
	else
		((heapsize++))
		parse "$fileName" "$heapsize"
	fi
}

dir=$1

if [ -z "$dir" ]
then
	echo Error: directory not provided
	exit 1
fi

find $dir -iname "*.java" | while read fileName; do
	parse "$fileName" 9
done
