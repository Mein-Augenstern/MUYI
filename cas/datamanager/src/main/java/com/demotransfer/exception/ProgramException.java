package com.demotransfer.exception;

/**
 * @Description: 通用业务异常类
 * @author: Administrator
 * @date: 2018年11月12日 下午11:10:20
 */
public class ProgramException extends RuntimeException {

	/**
	 * 序列化ID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 异常代码，需要项目统一定义
	 */
	private String code;

	/**
	 * 格式化参数
	 */
	private Object[] args;

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 */
	public ProgramException(String code) {
		this.code = code;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param args
	 *            异常信息中的格式化参数
	 */
	public ProgramException(String code, Object[] args) {
		this.code = code;
		this.args = args;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param e
	 *            内部异常
	 */
	public ProgramException(String code, Throwable e) {
		super(e);
		this.code = code;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param args
	 *            异常信息中的格式化参数
	 * @param e
	 *            内部异常
	 */
	public ProgramException(String code, Object[] args, Throwable e) {
		super(e);
		this.code = code;
		this.args = args;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param msg
	 *            异常描述
	 */
	public ProgramException(String code, String msg) {
		super(msg);
		this.code = code;
	}

	public ProgramException(Long code, String msg) {
		super(msg);
		this.code = code.toString();
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param args
	 *            异常信息中的格式化参数
	 * @param msg
	 *            异常描述
	 */
	public ProgramException(String code, Object[] args, String msg) {
		super(msg);
		this.code = code;
		this.args = args;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param msg
	 *            异常描述
	 * @param e
	 *            内部异常
	 */
	public ProgramException(String code, String msg, Throwable e) {
		super(msg, e);
		this.code = code;
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param msg
	 *            异常描述
	 * @param e
	 *            内部异常
	 */
	public ProgramException(Long code, String msg, Throwable e) {
		super(msg, e);
		this.code = code.toString();
	}

	/**
	 * 创建一个新的实例ProgramException.
	 * 
	 * @param code
	 *            异常码
	 * @param args
	 *            异常信息中的格式化参数
	 * @param msg
	 *            异常描述
	 * @param e
	 *            内部异常
	 */
	public ProgramException(String code, Object[] args, String msg, Throwable e) {
		super(msg, e);
		this.code = code;
		this.args = args;
	}

	/**
	 * 获取异常码
	 * 
	 * @author DemoTransfer 2017-11-4 下午05:01:39
	 * @return
	 */
	public String getCode() {
		return code;
	}

	/**
	 * 设置异常码
	 * 
	 * @author DemoTransfer 2017-11-4 下午05:01:45
	 * @param code
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * 获取格式化参数
	 * 
	 * @author DemoTransfer 2017-11-4 下午05:01:50
	 * @return
	 */
	public Object[] getArgs() {
		return args;
	}

	/**
	 * 设置格式化参数
	 * 
	 * @author DemoTransfer 2017-11-4 下午05:02:00
	 * @param args
	 */
	public void setArgs(Object[] args) {
		this.args = args;
	}
}
