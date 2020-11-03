package com.atguigu.crud.controller;

import com.atguigu.crud.bean.Employee;
import com.atguigu.crud.bean.Msg;
import com.atguigu.crud.service.EmployeeService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;

/**
 * 处理员工CRUD请求
 */
@Controller
public class EmployeeController {

    @Autowired
    EmployeeService employeeService;

    /**
     * 根据员工姓名进行查询
     * @param empName
     * @return
     */
    @RequestMapping(value = "/empName",method = RequestMethod.GET)
    @ResponseBody
    public Msg search(@RequestParam("empName")String empName){
        PageHelper.startPage(1, 5);
        //startPage后面紧跟的查询就是分页查询
        List<Employee> emps = employeeService.getByName(empName);
        System.out.println(emps);
        //使用pageInfo包装查询后的结果，只需要将pageInfo交给页码就行了
        //封装了详细的分页信息，包括我们查询出来的数据,传入连续显示的页数
        PageInfo page = new PageInfo(emps, 5);
        return Msg.succeed().add("pageInfo",page);
    }

    /**
     * 单个/批量删除合并
     * @param ids
     * @return
     */
    @RequestMapping(value = "/emp/{ids}",method = RequestMethod.DELETE)
    @ResponseBody
    public Msg deleteEmpById(@PathVariable("ids")String  ids){
        if(ids.contains("-")){
            //批量删除
            List<Integer> list = new ArrayList<Integer>();
            String[] str_ids = ids.split("-");
            for(String s : str_ids){
                list.add(Integer.parseInt(s));
            }
            employeeService.deleteBatch(list);
        }else{
            //单个删除
            employeeService.deleteEmp(Integer.parseInt(ids));
        }
        return Msg.succeed();
    }

    /**
     * 如果ajax直接发送PUT请求，我们封装的数据为：
     * Employee
     *      {empId=1036, empName='null', gender='null', email='null', dId=null, department=null}
     * 而在请求头中是有数据的，email=ast12123%40nn.com&gender=M&dId=1
     *
     * 问题就在于请求体中有数据，但是Employee对象封装不上
     * 这样的话SQL语句就变成 update tbl_employee where emp_id = 1014 ，没有set字段，所以sql语法就有问题
     *
     * 而封装不上的原因在于
     * Tomcat:
     *      tomcat会将请求体中的数据封装为一个map，request.getParameter("empName")就会从这个map中取值
     *      而SpringMVC封装POJO对象的时候，会把POJO中每个属性的值调用request.getParameter()方法来获取
     *
     *      但是如果AJAX发送PUT请求，tomcat看到是PUT请求，就不会将请求体中的数据封装为map，
     *      只有POST请求才会封装请求体为map
     *
     *解决方案：
     * 我们要能支持直接发送PUT之类的请求，还要封装请求体中的数据
     *      在web.xml中配置上HttpPutFormContentFilter过滤器
     *      他的作用是将请求体中的数据解析包装成map
     *      request被重新包装，request.getParameter()被重写，就会从自己封装的map中取出数据
     *
     * 员工更新方法
     * @param employee
     * @return
     */
    @RequestMapping(value = "/emp/{empId}",method = RequestMethod.PUT)
    @ResponseBody
    public Msg saveEmp(Employee employee){
        employeeService.updateEmp(employee);
        return Msg.succeed();
    }

    /**
     * 根据id查询员工
     * @param id
     * @return
     */
    @RequestMapping(value = "/emp/{id}", method = RequestMethod.GET)
    @ResponseBody
    public Msg getEmps(@PathVariable("id")Integer id){
        Employee employee = employeeService.getEmp(id);
        return Msg.succeed().add("emp",employee);
    }

    /**
     * 检查用户名是否可用
     * @param empName
     * @return
     */
    @RequestMapping("/checkUser")
    @ResponseBody
    public Msg checkUser(@RequestParam("empName")String empName){
        //先判断用户名是否是合法的表达式
        String regx = "(^[a-zA-Z0-9_-]{6,16}$)|(^[\\u2E80-\\u9FFF]{2,5})";
        if(!empName.matches(regx)){
            return Msg.fail().add("va_msg","用户名可以是2-5位中文或者6-16位的英文与数字的组合");
        }
        //数据库用户名重复校验
        boolean b = employeeService.checkUser(empName);
        if(b){
            return Msg.succeed();
        }else{
            return Msg.fail().add("va_msg","用户名不可用");
        }
    }

    /**
     * 导入jackson包，
     * @param pn
     * @return
     */
    @RequestMapping("/emps")
    @ResponseBody
    public Msg getEmpsWithJson(@RequestParam(value = "pn",defaultValue = "1")Integer pn){
        PageHelper.startPage(pn, 5);
        //startPage后面紧跟的查询就是分页查询
        List<Employee> emps = employeeService.getAll();
        //使用pageInfo包装查询后的结果，只需要将pageInfo交给页码就行了
        //封装了详细的分页信息，包括我们查询出来的数据,传入连续显示的页数
        PageInfo page = new PageInfo(emps, 5);
        return Msg.succeed().add("pageInfo",page);
    }

    /**
     * 保存员工数据
     * 1.支持JSR303校验
     * 2.导入Hibernate-Validator
     * @return
     */
    @RequestMapping(value = "/emp",method = RequestMethod.POST)
    @ResponseBody
    public Msg saveEmp(@Valid Employee employee, BindingResult result){
        if(result.hasErrors()){
            //校验失败，应该返回失败，在模态框中显示校验失败的错误信息
            Map<String, Object> map = new HashMap<String, Object>();
            List<FieldError> errors = result.getFieldErrors();
            for(FieldError fieldError : errors){
                System.out.println("错误的字段名："+fieldError.getField());
                System.out.println("错误的信息："+fieldError.getDefaultMessage());
                map.put(fieldError.getField(),fieldError.getDefaultMessage());
            }
            return Msg.fail().add("errorFields", map);
        }else{
            employeeService.saveEmp(employee);
            return Msg.succeed();
        }
    }
}
