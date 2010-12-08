package fx.sunjoy.server.cmd;

import java.io.BufferedOutputStream;
import java.util.Map;
import java.util.Map.Entry;

import fx.sunjoy.algo.impl.DiskTreap;

public class KMinCommand extends AbstractCommand{

	@Override
	public void execute(DiskTreap<String, byte[]> diskTreap,
			String command, byte[] body, BufferedOutputStream os)
			throws Exception {
		String[] stuff = command.split(" ");
		Integer k = Integer.parseInt(stuff[1]);
		Map<String, byte[]> result = diskTreap.kmin(k);
		for(Entry<String,byte[]> e :result.entrySet()){
			byte[] realvalue = new byte[e.getValue().length - 4] ;
			System.arraycopy(e.getValue(), 4, realvalue, 0, realvalue.length) ;
			os.write((e.getKey()+",\t"+realvalue+"\r\n").getBytes());
		}
		os.write(("END\r\n").getBytes());
		
	}

}
