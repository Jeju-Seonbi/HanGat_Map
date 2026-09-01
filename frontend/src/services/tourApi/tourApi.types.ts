export interface TourApiItem{contentid?:string;contenttypeid?:string;title?:string;addr1?:string;addr2?:string;mapx?:string;mapy?:string;firstimage?:string;firstimage2?:string;overview?:string;tel?:string;homepage?:string;modifiedtime?:string;originimgurl?:string;smallimageurl?:string;imgname?:string}
export interface TourApiHeader{resultCode?:string;resultMsg?:string}
export interface TourApiBody{items?:{item?:TourApiItem|TourApiItem[]}|'';totalCount?:number|string}
export interface TourApiResponse{response?:{header?:TourApiHeader;body?:TourApiBody}}
export interface TourApiBundle{list:TourApiItem;detail?:TourApiItem;images:TourApiItem[]}
export class TourApiError extends Error{constructor(message:string,public readonly code:'MISSING_KEY'|'TIMEOUT'|'HTTP'|'API'|'INVALID_RESPONSE'){super(message);this.name='TourApiError'}}
