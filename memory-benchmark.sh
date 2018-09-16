
function parse()
{
	parser="$1"
	fileName="$2"
	heapsize="$3"

    result=`java -Xss4m -XX:+UseG1GC "-Xmx${heapsize}m" -cp target/benchmarks.jar "iguana.SingleFile${parser}Run" "$fileName" 2>/dev/null`

	if [ $? -eq 0 ]; then
		echo "${result}${heapsize}"
	else
		((heapsize++))
		parse "$parser" "$fileName" "$heapsize"
	fi
}

parser=$1
dir=$2

if [ -z "$parser" ]
then
	echo Error: parser not provided
	exit 1
fi

if [ -z "$dir" ]
then
	echo Error: directory not provided
	exit 1
fi

find $dir -iname "*.java" | while read fileName; do
	parse "$parser" "$fileName" 5
done
