package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.pojo.PageResult;
import com.leyou.item.bo.SpuBo;
import com.leyou.item.mapper.*;
import com.leyou.item.pojo.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class GoodsService {

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private SpuDetailMapper spuDetailMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private Logger logger = LoggerFactory.getLogger(GoodsService.class);
    public PageResult<SpuBo> querySpuBoByPage(String key, Boolean saleable, Integer page, Integer rows) {
/*
        Example example = new Example(Spu.class);
        Example.Criteria criteria = example.createCriteria();
        // 搜索条件
        if (StringUtils.isNotBlank(key)) {
            criteria.andLike("title", "%" + key + "%");
        }
        if (saleable != null) {
            criteria.andEqualTo("saleable", saleable);
        }

        // 分页条件
        PageHelper.startPage(page, rows);

        // 执行查询
        List<Spu> spus = this.spuMapper.selectByExample(example);
        PageInfo<Spu> pageInfo = new PageInfo<>(spus);
        //
        List<SpuBo> spuBos = new ArrayList<>();
        spus.forEach(spu->{
            SpuBo spuBo = new SpuBo();
            // copy共同属性的值到新的对象
            BeanUtils.copyProperties(spu, spuBo);
            // 查询分类名称
            List<String> names = this.categoryService.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
            spuBo.setCname(StringUtils.join(names, "/"));

            // 查询品牌的名称
            spuBo.setBname(this.brandMapper.selectByPrimaryKey(spu.getBrandId()).getName());

            spuBos.add(spuBo);
        });*/
        Example example = new Example( Spu.class );
        Example.Criteria criteria = example.createCriteria();
        //1、添加查询条件
        if (StringUtils.isNotBlank( key )){
        criteria.andLike( "title","%"+key+"%" );
        }
        //2.添加过滤条件
        if (saleable != null) {
            criteria.andEqualTo("saleable", saleable);
        }
        //3、添加分页
        PageHelper.startPage( page,rows );
        //4、执行查询
        List<Spu> spus = spuMapper.selectByExample( example );
        PageInfo<Spu> pageInfo = new PageInfo<>(spus);
        //5、spubo
        List<SpuBo> spuBos = new ArrayList<>();
        spus.forEach( spu -> {
            SpuBo spuBo = new SpuBo();
            //复制属性到新对象中
            BeanUtils.copyProperties( spu,spuBo );
            List<String> names = categoryService.queryNamesByIds( Arrays.asList( spu.getCid1(),spu.getCid2() ,spu.getCid3()) );
            spuBo.setCname(StringUtils.join(names, "/"));
            spuBo.setBname( brandMapper.selectByPrimaryKey( spu.getBrandId() ).getName() );
            spuBos.add( spuBo );
        } );

        return new PageResult<>(pageInfo.getTotal(), spuBos);

    }

    /**
     * 新增商品 ：要新增多个表，逻辑较为复杂
     * @param spuBo
     */
    @Transactional
    public void saveGoods(SpuBo spuBo) {
        // 新增spu
        // 设置默认字段
        spuBo.setId(null);
        spuBo.setSaleable(true);
        spuBo.setValid(true);
        spuBo.setCreateTime(new Date());
        spuBo.setLastUpdateTime(spuBo.getCreateTime());
        this.spuMapper.insertSelective(spuBo);

        // 新增spuDetail
        SpuDetail spuDetail = spuBo.getSpuDetail();
        spuDetail.setSpuId(spuBo.getId());
        this.spuDetailMapper.insertSelective(spuDetail);

        //新增SKU
        List<Sku> skus = spuBo.getSkus();
        skus.forEach( sku -> {
            sku.setSpuId( spuBo.getId() );
            sku.setCreateTime( new Date() );
            sku.setLastUpdateTime( spuBo.getCreateTime());
            skuMapper.insertSelective(sku);

            //新增库存
            Stock stock = new Stock();
            stock.setSkuId( sku.getId() );
            stock.setStock(sku.getStock());
            stockMapper.insertSelective( stock );
        } );



        saveSkuAndStock(spuBo);
        //向消息队列发送消息
       this.sendMessage(spuBo.getId(),"insert");
    }

    private void saveSkuAndStock(SpuBo spuBo) {
        spuBo.getSkus().forEach(sku -> {
            // 新增sku
            sku.setSpuId(spuBo.getId());
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            this.skuMapper.insertSelective(sku);

            // 新增库存
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            this.stockMapper.insertSelective(stock);
        });
    }

    /**
     * 根据spuId查询spuDetail
     * @param spuId
     * @return
     */
    public SpuDetail querySpuDetailBySpuId(Long spuId) {

        return this.spuDetailMapper.selectByPrimaryKey(spuId);
    }

    /**
     * 根据spuId查询sku的集合
     * @param spuId
     * @return
     */
    public List<Sku> querySkusBySpuId(Long spuId) {
        Sku sku = new Sku();
        sku.setSpuId(spuId);
        List<Sku> skus = this.skuMapper.select(sku);
        skus.forEach(s -> {
            Stock stock = this.stockMapper.selectByPrimaryKey(s.getId());
            s.setStock(stock.getStock());
        });
        return skus;
    }

    @Transactional
    public void update(SpuBo spuBo) {
        // 查询以前sku
        List<Sku> skus = this.querySkusBySpuId(spuBo.getId());

        // 如果以前存在，则删除
        skus.forEach(sku -> {
            //删除sku对应的库存
            this.stockMapper.deleteByPrimaryKey(sku.getId());
        });

        //删除sku
        Sku record = new Sku();
        record.setSpuId(spuBo.getId());
        this.skuMapper.delete(record);

        // 新增sku和库存
        saveSkuAndStock(spuBo);

        // 更新spu
        spuBo.setLastUpdateTime(new Date());
        spuBo.setCreateTime(null);
        spuBo.setValid(null);
        spuBo.setSaleable(null);
        this.spuMapper.updateByPrimaryKeySelective(spuBo);

        // 更新spu详情
        this.spuDetailMapper.updateByPrimaryKeySelective(spuBo.getSpuDetail());

        //向消息队列发送消息
        this.sendMessage(spuBo.getId(),"update");
    }

    public Spu querySpuById(Long id) {
        return this.spuMapper.selectByPrimaryKey(id);
    }

    /**
     * 向rabbitmq中发送消息
     * @param id
     * @param type
     */
    public void sendMessage(Long id,String type){
        //发送消息
        try {
            amqpTemplate.convertAndSend("item." + type,id);
        }catch (Exception e){
            logger.error("{}商品消息发送异常，商品id：{}", type, id, e);
        }
    }
    /**
     * 通过skuid查询sku
     */
    public Sku querySkuById(Long id) {

        Sku sku = this.skuMapper.selectByPrimaryKey(id);
        return sku;
    }
}
