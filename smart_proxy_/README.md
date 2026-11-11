How to run?

1. Edit .env.example

2. Run Redis

3. Run smart proxy

This proxy will user DW on port 10000 and 10001. Make sure that your DW 
are running on 10000 and 10001. 

We're using AP from CAP. If one DW is not running, smartProxy will use second
DW. Also, we're using Round-Robin, because why not.   
