package fx.sunjoy.algo.impl;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import fx.sunjoy.algo.ITreap;
import fx.sunjoy.utils.BlockUtil;
import fx.sunjoy.utils.ByteUtil;



public class DiskTreap<K extends Comparable<K>,V extends Serializable> implements ITreap<K, V> {

	private static final int DEFAULT_BLOCK_SIZE = 440;

	//用于读写文件
	private final BlockUtil<K, V> blockUtil;
	
	//控制读写并发，读时可以读，读时不能写，写时不能读
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	
	private final LRUMap<K, V> valueCache = new LRUMap<K, V>(100000);
	private final LRUMap<K, V> bigValueCache = new LRUMap<K, V>(100);
	
	//用户可以自行设置block_size来调整索引item的大小,默认为DEFAULT_BLOCK_SIZE
	public DiskTreap(int block_size, File _indexFile,long mmapSize) throws Exception{
		this.blockUtil = new BlockUtil<K,V>(block_size, _indexFile,mmapSize);
	}
	
	public DiskTreap(int block_size, File _indexFile) throws Exception{
		this.blockUtil = new BlockUtil<K,V>(block_size, _indexFile,64<<20);
	}
	
	public DiskTreap(File _indexFile) throws Exception{
		this(DEFAULT_BLOCK_SIZE,_indexFile,64<<20);
	}
	
	public void finalize(){
		this.close();
	}
	
	//关闭索引和数据文件
	public void close(){
		try{
			if(this.blockUtil!=null)this.blockUtil.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//写入
	/* (non-Javadoc)
	 * @see fx.sunjoy.algo.impl.ITreap#put(K, V)
	 */
	@Override
	public void put(K key,V value){
		DiskTreapHeader header;
		try {
			lock.writeLock().lock();
			if(ByteUtil.isSmallObj(value)){
				valueCache.put(key, value);
				bigValueCache.remove(key);
			}else{
				valueCache.remove(key);
				bigValueCache.put(key, value);
			}
			header = this.blockUtil.readHeader();
			int rootNo = insert(header.rootNo,key,value);
			header = this.blockUtil.readHeader();
			header.rootNo = rootNo;
			this.blockUtil.writeHeader(header);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
			lock.writeLock().unlock();
		}
	}
	
	//读出
	/* (non-Javadoc)
	 * @see fx.sunjoy.algo.impl.ITreap#get(K)
	 */
	@Override
	public V get(K key){
		lock.readLock().lock();
		DiskTreapHeader header;
		try {
			if(valueCache.containsKey(key)){
				return valueCache.get(key);
			}else if(bigValueCache.containsKey(key)){
				return bigValueCache.get(key);
			}
			header = this.blockUtil.readHeader();
			int idx =  find(header.rootNo,key);
			if(idx==-1)return null;
			else{
				DiskTreapNode< K, V> node = this.blockUtil.readNode(idx, true);
				if(ByteUtil.isSmallObj(node.value)){
					valueCache.put(key, node.value);
					bigValueCache.remove(key);
				}else{
					valueCache.remove(key);
					bigValueCache.put(key, node.value);
				}
				return node.value;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}finally{
			lock.readLock().unlock();
		}
	}

	

	@Override
	/**
	 * 取key的范围在[start,end)区间的数据
	 */
	public Map<K,V> range(K start, K end,int limit) {
		lock.readLock().lock();
		try {
			Map<K,V> result = new TreeMap<K, V>();
			DiskTreapHeader header = this.blockUtil.readHeader();
			collectRange(header.rootNo, start, end, result,limit);
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
			lock.readLock().unlock();
		}
	}

	/*
	 * 取k个最小的
	 */
	public Map<K,V> kmin(int k){
		lock.readLock().lock();
		try {
			Map<K,V> result = new TreeMap<K, V>();
			DiskTreapHeader header = this.blockUtil.readHeader();
			if(k>header.size)k=header.size;
			if(k<0)k=0;
			collectKMin(header.rootNo, k,result);
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
			lock.readLock().unlock();
		}
	}
	
	public Map<K,V> kmax(int k){
		lock.readLock().lock();
		try {
			Map<K,V> result = new TreeMap<K, V>();
			DiskTreapHeader header = this.blockUtil.readHeader();
			if(k>header.size)k=header.size;
			if(k<0)k=0;
			collectKMax(header.rootNo, k,result);
			return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
			lock.readLock().unlock();
		}
	}
	
	private void collectKMax(int startNode, int k, Map<K, V> result) throws Exception {
		if(startNode==-1){
			return;
		}
		DiskTreapNode<K, V> node =  this.blockUtil.readNode(startNode,true);
		if(k<=node.r_size){
			collectKMax(node.rNo,k,result);
		}else if(k==node.r_size+1){
			collectKMax(node.rNo,node.r_size,result);
			result.put(node.key, node.value);
		}else{
			collectKMax(node.rNo,node.r_size,result);
			result.put(node.key, node.value);
			collectKMax(node.lNo,k-node.r_size-1,result);
		}
	}

	private void collectKMin(int startNode, int k,Map<K,V> result) throws Exception {
		if(startNode==-1){
			return;
		}
		DiskTreapNode<K, V> node =  this.blockUtil.readNode(startNode,true);
		if(k<=node.l_size){
			collectKMin(node.lNo,k,result);
		}else if(k==node.l_size+1){
			collectKMin(node.lNo,node.l_size,result);
			result.put(node.key, node.value);
		}else{
			collectKMin(node.lNo,node.l_size,result);
			result.put(node.key, node.value);
			collectKMin(node.rNo,k-node.l_size-1,result);
		}
	}

	private void collectRange(int startNode, K start, K end,Map<K,V> values,int limit) throws Exception{
		if(startNode==-1){
			return;
		}
		if(start.compareTo(end)>=0){
			throw new RuntimeException("invalid range:"+start+" to "+ end);
		}
		if(values.size()>=limit){
			return ;
		}
		DiskTreapNode<K, V> node =  this.blockUtil.readNode(startNode,true);
		int cp1 = node.key.compareTo(start);
		int cp2 = node.key.compareTo(end);
		
		if(cp1>=0 && cp2<0){
			collectRange(node.rNo, start, end, values,limit);
			if(values.size()>=limit){
				return ;
			}
			values.put(node.key, node.value);
			collectRange(node.lNo, start, end, values,limit);
		}
		if(cp1<0)
			collectRange(node.rNo, start, end, values,limit);
		if(cp2>=0)
			collectRange(node.lNo, start, end, values,limit);
	}
	
	@Override
	/**
	 * 库中数据条目数
	 */
	public int length() {
		lock.readLock().lock();
		try {
			DiskTreapHeader header = this.blockUtil.readHeader();
			int rootNo = header.rootNo;
			//System.out.println("rootNo:"+rootNo);
			if(rootNo==-1){
				return 0;
			}
			DiskTreapNode<K,V> root = this.blockUtil.readNode(rootNo, false);
			return root.l_size+1+root.r_size;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}finally{
			lock.readLock().unlock();
		}
	}

	@Override
	//删除，暂时没有实现
	public void delete(K key) {
		
	}

	//前缀搜索
	public Map<K,V> prefix(K prefixString,int limit) {
		lock.readLock().lock();
		try {
			Map<K,V> results = new TreeMap<K,V>();
			DiskTreapHeader header = this.blockUtil.readHeader();
			prefixSearch(header.rootNo,prefixString,results,limit);
			return results;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
			lock.readLock().unlock();
		}
	}
	
	private void prefixSearch(int startNode, K prefixString,Map<K,V> results,int limit) throws Exception {
		if(startNode==-1)return ;
		if(results.size()>=limit)return;
		DiskTreapNode<K, V> cur = this.blockUtil.readNode(startNode, true);
		if(prefixString.compareTo(cur.key)<=0){
			if(isPrefixString(prefixString.toString(),cur.key.toString())){
				prefixSearch(cur.lNo, prefixString,results,limit);
				if(results.size()>=limit)return;
				results.put(cur.key, cur.value);
				prefixSearch(cur.rNo, prefixString,results,limit);
			}else{
				prefixSearch(cur.lNo, prefixString,results,limit);
			}
		}else{
			prefixSearch(cur.rNo, prefixString,results,limit);
		}
	}
	
	private boolean isPrefixString(String prefixString, String key) {
		if(key.indexOf(prefixString)==0)return true;
		return false;
	}

	
	private int insert(int startNode,K key,V value) throws Exception{
		if(startNode==-1){
			DiskTreapHeader header = this.blockUtil.readHeader();
			DiskTreapNode<K,V> newNode = new DiskTreapNode<K,V>();
			newNode.key = key;
			newNode.value = value;
			newNode.fix = (int)(Math.random()*Integer.MAX_VALUE);
			this.blockUtil.writeNode(header.size, newNode, true);
			header.size++;
			this.blockUtil.writeHeader(header);
			return header.size-1;
		}else{
			DiskTreapNode<K,V> currentNode = this.blockUtil.readNode(startNode, false);
			int cp = currentNode.key.compareTo(key);
			if(cp==0){
				currentNode.value = value;
				this.blockUtil.writeNode(startNode, currentNode, true);
			}else if(cp<0){
				currentNode.rNo = insert(currentNode.rNo,key,value);
				this.blockUtil.writeNode(startNode, currentNode, false);
				DiskTreapNode<K, V> rightNode = this.blockUtil.readNode(currentNode.rNo, false);
				currentNode.r_size = rightNode.r_size+1+rightNode.l_size;
				if(rightNode.fix < currentNode.fix){
					startNode = rotateLeft(startNode);
				}
				
			}else if(cp>0){
				currentNode.lNo = insert(currentNode.lNo,key,value);
				this.blockUtil.writeNode(startNode, currentNode, false);
				DiskTreapNode< K, V> leftNode = this.blockUtil.readNode(currentNode.lNo, false);
				currentNode.l_size = leftNode.l_size+1+leftNode.r_size;
				if(leftNode.fix < currentNode.fix){
					startNode = rotateRight(startNode);
				}
			}
		}
		return startNode;
	}
	

	//右旋
	private int rotateRight(int startNode) throws Exception{
		DiskTreapNode<K,V> cur = this.blockUtil.readNode(startNode, false);
		int leftNo = cur.lNo;
		DiskTreapNode<K,V> left = this.blockUtil.readNode(leftNo, false);
		int left_right = left.rNo;
		int left_right_size  = left.r_size;
		left.rNo = startNode;
		left.r_size += cur.r_size+1;
		cur.lNo  = left_right;
		cur.l_size = left_right_size;
		this.blockUtil.writeNode(startNode, cur, false);
		this.blockUtil.writeNode(leftNo, left, false);
		return leftNo;
	}
	
	//左旋
	private int rotateLeft(int startNode) throws Exception{
		DiskTreapNode<K,V> cur = this.blockUtil.readNode(startNode, false);
		int rightNo = cur.rNo;
		DiskTreapNode <K,V> right = this.blockUtil.readNode(rightNo, false);
		int right_left = right.lNo;
		int right_left_size = right.l_size;
		right.lNo = startNode;
		right.l_size += cur.l_size+1;
		cur.rNo = right_left;
		cur.r_size = right_left_size;
		this.blockUtil.writeNode(startNode, cur, false);
		this.blockUtil.writeNode(rightNo, right, false);
		return rightNo;
	}
	
	private int find(int startNode,K key) throws Exception{
		if(startNode==-1){
			return -1;
		}else{
			DiskTreapNode<K, V> currentNode = this.blockUtil.readNode(startNode, false);
			int cp = currentNode.key.compareTo(key);
			if(cp==0){
				return startNode;
			}else if(cp<0){
				return find(currentNode.rNo,key);
			}else if(cp>0){
				return  find(currentNode.lNo,key);
			}
		}
		return -1;
	}

}
